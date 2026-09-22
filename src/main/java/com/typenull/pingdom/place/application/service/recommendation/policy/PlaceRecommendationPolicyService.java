package com.typenull.pingdom.place.application.service.recommendation.policy;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.VersionPolicy;
import com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 설정된 추천 알고리즘에 DB의 트래픽 배분·킬 스위치를 겹쳐 요청에 적용할 정책을 선택.
 * 정책 변경은 기존 저장 행을 잠가 반영하고 커밋 후 현재 인스턴스의 메모리 정책을 다시 읽음.
 * 버전·배분·폴백의 관리자 입력 검증은 호출 측이 맡으며 다른 서버의 메모리는 직접 갱신 대상에서 제외.
 */
@Service
public class PlaceRecommendationPolicyService {

    private final PlaceRecommendationProperties properties;
    private final PlaceRecommendationTrafficPolicyRepository trafficPolicyRepository;
    private volatile Map<String, VersionPolicy> policiesByVersion = Map.of();
    private volatile Map<String, Integer> trafficOverridesByVersion = Map.of();
    private volatile Map<String, KillSwitchOverride> killSwitchOverridesByVersion = Map.of();

    public PlaceRecommendationPolicyService(
            PlaceRecommendationProperties properties,
            PlaceRecommendationTrafficPolicyRepository trafficPolicyRepository
    ) {
        this.properties = properties;
        this.trafficPolicyRepository = trafficPolicyRepository;
    }

    @PostConstruct
    void initialize() {
        refreshPolicies();
    }

    /**
     * 설정의 버전 정책과 DB의 트래픽·활성 여부를 읽어 현재 인스턴스의 정책 캐시를 교체. 설정 버전이 없으면 기본 정책을 사용.
     * synchronized는 갱신 호출끼리만 조정하며 잠금 없이 읽는 resolve에 여러 맵의 동시 교체는 보장 범위에서 제외.
     */
    @Transactional(readOnly = true)
    public synchronized void refreshPolicies() {
        List<VersionPolicy> configuredPolicies = properties.versions();
        if (configuredPolicies == null || configuredPolicies.isEmpty()) {
            configuredPolicies = defaultPolicies();
        }

        Map<String, VersionPolicy> policyMap = new LinkedHashMap<>();
        for (VersionPolicy policy : configuredPolicies) {
            policyMap.put(policy.version(), policy);
        }

        String defaultVersion = StringUtils.hasText(properties.defaultVersion())
                ? properties.defaultVersion()
                : "place-rec-v1";
        if (!policyMap.containsKey(defaultVersion)) {
            defaultVersion = policyMap.keySet().iterator().next();
        }

        policiesByVersion = Map.copyOf(policyMap);
        resolvedDefaultVersion = defaultVersion;
        List<PlaceRecommendationTrafficPolicy> storedPolicies = trafficPolicyRepository.findAll();
        trafficOverridesByVersion = storedPolicies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PlaceRecommendationTrafficPolicy::getRecommendationVersion,
                        PlaceRecommendationTrafficPolicy::getTrafficPercentage
                ));
        killSwitchOverridesByVersion = storedPolicies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PlaceRecommendationTrafficPolicy::getRecommendationVersion,
                        policy -> new KillSwitchOverride(policy.isEnabled(), policy.getFallbackVersion())
                ));
    }

    private volatile String resolvedDefaultVersion;

    /**
     * 알려진 요청 버전을 우선하고 미지정이면 사용자 또는 좌표 버킷에 따라 정책을 선택.
     * 알 수 없는 버전·비활성 정책은 기본값과 fallback 경로로 해소하여 실제 버전 및 전환 사유를 반환.
     * 정책 DB를 직접 조회하지 않고 현재 인스턴스의 메모리 캐시를 사용.
     */
    public ResolvedRecommendationPolicy resolve(
            Long userId,
            double latitude,
            double longitude,
            String requestedVersion
    ) {
        if (StringUtils.hasText(requestedVersion)) {
            VersionPolicy requestedPolicy = policiesByVersion.get(requestedVersion.trim());
            if (requestedPolicy != null) {
                return resolveActivePolicy(requestedPolicy.version(), Set.of(), requestedPolicy.version(), "requested_version_disabled");
            }
            return resolveActivePolicy(resolvedDefaultVersion, Set.of(), requestedVersion.trim(), "requested_version_not_found");
        }

        int bucket = resolveBucket(userId, latitude, longitude);
        int cumulative = 0;
        for (VersionPolicy policy : policiesByVersion.values()) {
            cumulative += currentTrafficPercentage(policy);
            if (bucket < cumulative) {
                return resolveActivePolicy(policy.version(), Set.of(), policy.version(), "bucket_version_disabled");
            }
        }

        return resolveActivePolicy(resolvedDefaultVersion, Set.of(), resolvedDefaultVersion, "default_version_disabled");
    }

    private int resolveBucket(Long userId, double latitude, double longitude) {
        if (userId != null) {
            return Math.floorMod(Long.hashCode(userId), 100);
        }

        int latitudeBucket = (int) Math.round(latitude * 10_000d);
        int longitudeBucket = (int) Math.round(longitude * 10_000d);
        return Math.floorMod((31 * latitudeBucket) + longitudeBucket, 100);
    }

    @Transactional(readOnly = true)
    public List<RecommendationTrafficPolicy> getTrafficPolicies() {
        return buildTrafficPolicies(trafficOverridesByVersion);
    }

    /**
     * 기존 정책 행을 쓰기 잠금으로 읽고 알려진 버전 중 전달된 명령만 DB에 저장하여 반영 예정 정책 목록을 반환.
     * 트랜잭션 동기화가 있으면 커밋 후 로컬 캐시를 새로 읽고, 없으면 즉시 갱신.
     * 전체 비율 합계·fallback 관계 검증은 관리자 서비스가 선행하며 다른 인스턴스 캐시는 이 메서드의 갱신 대상에서 제외.
     */
    @Transactional
    public List<RecommendationTrafficPolicy> updateTrafficPolicies(Map<String, PolicyUpdateCommand> policyCommands) {
        Map<String, Integer> updatedOverrides = new LinkedHashMap<>(trafficOverridesByVersion);
        Map<String, KillSwitchOverride> updatedKillSwitchOverrides = new LinkedHashMap<>(killSwitchOverridesByVersion);
        Map<String, PlaceRecommendationTrafficPolicy> existingPolicies = trafficPolicyRepository.findAllForUpdate().stream()
                .collect(Collectors.toMap(
                        PlaceRecommendationTrafficPolicy::getRecommendationVersion,
                        policy -> policy
                ));
        for (VersionPolicy policy : policiesByVersion.values()) {
            PolicyUpdateCommand command = policyCommands.get(policy.version());
            if (command == null) {
                continue;
            }

            PlaceRecommendationTrafficPolicy savedPolicy = existingPolicies.get(policy.version());
            if (savedPolicy == null) {
                savedPolicy = PlaceRecommendationTrafficPolicy.create(
                        policy.version(),
                        command.trafficPercentage(),
                        command.enabled(),
                        command.fallbackVersion()
                );
            }
            savedPolicy.update(command.trafficPercentage(), command.enabled(), command.fallbackVersion());
            trafficPolicyRepository.save(savedPolicy);
            updatedOverrides.put(policy.version(), command.trafficPercentage());
            updatedKillSwitchOverrides.put(policy.version(), new KillSwitchOverride(command.enabled(), command.fallbackVersion()));
        }

        registerRefreshAfterCommit();
        return buildTrafficPolicies(updatedOverrides, updatedKillSwitchOverrides);
    }

    private List<RecommendationTrafficPolicy> buildTrafficPolicies(Map<String, Integer> trafficOverrides) {
        return buildTrafficPolicies(trafficOverrides, killSwitchOverridesByVersion);
    }

    private List<RecommendationTrafficPolicy> buildTrafficPolicies(
            Map<String, Integer> trafficOverrides,
            Map<String, KillSwitchOverride> killSwitchOverrides
    ) {
        return policiesByVersion.values().stream()
                .map(policy -> new RecommendationTrafficPolicy(
                        policy.version(),
                        policy.stage() == null ? RecommendationStage.STABLE : policy.stage(),
                        currentTrafficPercentage(policy, trafficOverrides),
                        isEnabled(policy.version(), killSwitchOverrides),
                        fallbackVersion(policy.version(), killSwitchOverrides)
                ))
                .toList();
    }

    public String getDefaultVersion() {
        return resolvedDefaultVersion;
    }

    public boolean supportsVersion(String version) {
        return StringUtils.hasText(version) && policiesByVersion.containsKey(version.trim());
    }

    private int currentTrafficPercentage(VersionPolicy policy) {
        return currentTrafficPercentage(policy, trafficOverridesByVersion);
    }

    private int currentTrafficPercentage(VersionPolicy policy, Map<String, Integer> trafficOverrides) {
        return trafficOverrides.getOrDefault(policy.version(), policy.trafficPercentage());
    }

    private ResolvedRecommendationPolicy resolveActivePolicy(
            String version,
            Set<String> visitedVersions,
            String sourceVersion,
            String fallbackReason
    ) {
        VersionPolicy policy = policiesByVersion.get(version);
        if (policy == null) {
            return resolveFirstEnabledPolicy(sourceVersion, fallbackReason);
        }
        if (isEnabled(policy.version(), killSwitchOverridesByVersion)) {
            return ResolvedRecommendationPolicy.from(policy, sourceVersion, fallbackReason);
        }

        if (visitedVersions.contains(policy.version())) {
            return resolveFirstEnabledPolicy(sourceVersion, "fallback_cycle_detected");
        }

        String fallbackVersion = fallbackVersion(policy.version(), killSwitchOverridesByVersion);
        if (StringUtils.hasText(fallbackVersion) && policiesByVersion.containsKey(fallbackVersion)) {
            Set<String> nextVisitedVersions = new java.util.HashSet<>(visitedVersions);
            nextVisitedVersions.add(policy.version());
            return resolveActivePolicy(fallbackVersion, nextVisitedVersions, sourceVersion, fallbackReason);
        }

        return resolveFirstEnabledPolicy(sourceVersion, "fallback_version_missing");
    }

    /**
     * 폴백 체인이 끊기거나 순환하면 활성 정책을 찾음.
     * 활성 정책이 하나도 없을 때도 요청을 실패시키지 않고 기본 정책을 반환하는 현재 폴백 규칙.
     */
    private ResolvedRecommendationPolicy resolveFirstEnabledPolicy(String sourceVersion, String fallbackReason) {
        for (VersionPolicy candidate : policiesByVersion.values()) {
            if (isEnabled(candidate.version(), killSwitchOverridesByVersion)) {
                return ResolvedRecommendationPolicy.from(candidate, sourceVersion, fallbackReason);
            }
        }
        return ResolvedRecommendationPolicy.from(
                Objects.requireNonNull(policiesByVersion.get(resolvedDefaultVersion)),
                sourceVersion,
                fallbackReason
        );
    }

    private boolean isEnabled(String version, Map<String, KillSwitchOverride> killSwitchOverrides) {
        return killSwitchOverrides.getOrDefault(version, KillSwitchOverride.DEFAULT).enabled();
    }

    private String fallbackVersion(String version, Map<String, KillSwitchOverride> killSwitchOverrides) {
        return killSwitchOverrides.getOrDefault(version, KillSwitchOverride.DEFAULT).fallbackVersion();
    }

    private void registerRefreshAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshPolicies();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshPolicies();
            }
        });
    }

    private List<VersionPolicy> defaultPolicies() {
        return List.of(
                new VersionPolicy(
                        "place-rec-v1",
                        RecommendationStage.STABLE,
                        100,
                        false,
                        4,
                        0.75d,
                        0.10d,
                        0.15d,
                        0.05d,
                        0.05d,
                        new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                        new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d, 0.0d),
                        new RankingWeights(0.48d, 0.0d, 0.16d, 0.10d, 0.08d, 0.12d, 0.09d, 0.0d)
                ),
                new VersionPolicy(
                        "place-rec-v2",
                        RecommendationStage.EXPERIMENTAL,
                        0,
                        true,
                        5,
                        0.70d,
                        0d,
                        0d,
                        0.05d,
                        0.05d,
                        new CandidateMix(0.40d, 0.20d, 0.25d, 0.15d),
                        new RankingWeights(0.25d, 0.29d, 0.10d, 0.07d, 0.08d, 0.05d, 0.06d, 0.10d),
                        new RankingWeights(0.36d, 0.0d, 0.13d, 0.09d, 0.09d, 0.14d, 0.09d, 0.10d)
                )
        );
    }

    public record ResolvedRecommendationPolicy(
            String version,
            RecommendationStage stage,
            boolean featureLoggingEnabled,
            int portfolioSizeMultiplier,
            double mmrRelevanceWeight,
            double interestMatchBoost,
            double intentMatchBoost,
            double benefitBoost,
            double availabilityBoost,
            PlaceRecommendationProperties.CandidateMix mix,
            PlaceRecommendationProperties.RankingWeights personalizedWeights,
            PlaceRecommendationProperties.RankingWeights anonymousWeights,
            String sourceVersion,
            String fallbackReason
    ) {
        public ResolvedRecommendationPolicy(
                String version,
                RecommendationStage stage,
                boolean featureLoggingEnabled,
                int portfolioSizeMultiplier,
                double mmrRelevanceWeight,
                double interestMatchBoost,
                double intentMatchBoost,
                PlaceRecommendationProperties.CandidateMix mix,
                PlaceRecommendationProperties.RankingWeights personalizedWeights,
                PlaceRecommendationProperties.RankingWeights anonymousWeights,
                String sourceVersion,
                String fallbackReason
        ) {
            this(
                    version, stage, featureLoggingEnabled, portfolioSizeMultiplier, mmrRelevanceWeight,
                    interestMatchBoost, intentMatchBoost, 0d, 0d, mix, personalizedWeights,
                    anonymousWeights, sourceVersion, fallbackReason
            );
        }

        private static ResolvedRecommendationPolicy from(VersionPolicy policy, String sourceVersion, String fallbackReason) {
            return new ResolvedRecommendationPolicy(
                    policy.version(),
                    policy.stage() == null ? RecommendationStage.STABLE : policy.stage(),
                    policy.featureLoggingEnabled(),
                    policy.portfolioSizeMultiplier(),
                    policy.mmrRelevanceWeight(),
                    policy.interestMatchBoost(),
                    policy.intentMatchBoost(),
                    policy.benefitBoost(),
                    policy.availabilityBoost(),
                    policy.mix(),
                    policy.personalizedWeights(),
                    policy.anonymousWeights(),
                    sourceVersion,
                    fallbackReason
            );
        }

        public PlaceRecommendationProperties.RankingWeights weights(boolean hasPersonalSignals) {
            return hasPersonalSignals ? personalizedWeights : anonymousWeights;
        }
    }

    public record RecommendationTrafficPolicy(
            String version,
            RecommendationStage stage,
            int trafficPercentage,
            boolean enabled,
            String fallbackVersion
    ) {
    }

    public record PolicyUpdateCommand(
            int trafficPercentage,
            boolean enabled,
            String fallbackVersion
    ) {
    }

    private record KillSwitchOverride(
            boolean enabled,
            String fallbackVersion
    ) {
        private static final KillSwitchOverride DEFAULT = new KillSwitchOverride(true, null);
    }
}
