package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.VersionPolicy;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceRecommendationPolicyService {

    private final PlaceRecommendationProperties properties;
    private final PlaceRecommendationTrafficPolicyRepository trafficPolicyRepository;
    private volatile Map<String, VersionPolicy> policiesByVersion = Map.of();
    private volatile Map<String, Integer> trafficOverridesByVersion = Map.of();

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
        trafficOverridesByVersion = trafficPolicyRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        PlaceRecommendationTrafficPolicy::getRecommendationVersion,
                        PlaceRecommendationTrafficPolicy::getTrafficPercentage
                ));
    }

    private volatile String resolvedDefaultVersion;

    public ResolvedRecommendationPolicy resolve(
            Long userId,
            double latitude,
            double longitude,
            String requestedVersion
    ) {
        if (StringUtils.hasText(requestedVersion)) {
            VersionPolicy requestedPolicy = policiesByVersion.get(requestedVersion.trim());
            if (requestedPolicy != null) {
                return ResolvedRecommendationPolicy.from(requestedPolicy);
            }
            return ResolvedRecommendationPolicy.from(policiesByVersion.get(resolvedDefaultVersion));
        }

        int bucket = resolveBucket(userId, latitude, longitude);
        int cumulative = 0;
        for (VersionPolicy policy : policiesByVersion.values()) {
            cumulative += currentTrafficPercentage(policy);
            if (bucket < cumulative) {
                return ResolvedRecommendationPolicy.from(policy);
            }
        }

        return ResolvedRecommendationPolicy.from(policiesByVersion.get(resolvedDefaultVersion));
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
        return policiesByVersion.values().stream()
                .map(policy -> new RecommendationTrafficPolicy(
                        policy.version(),
                        policy.stage() == null ? RecommendationStage.STABLE : policy.stage(),
                        currentTrafficPercentage(policy)
                ))
                .toList();
    }

    @Transactional
    public synchronized List<RecommendationTrafficPolicy> updateTrafficPolicies(Map<String, Integer> trafficByVersion) {
        for (VersionPolicy policy : policiesByVersion.values()) {
            Integer trafficPercentage = trafficByVersion.get(policy.version());
            if (trafficPercentage == null) {
                continue;
            }

            PlaceRecommendationTrafficPolicy savedPolicy = trafficPolicyRepository.findById(policy.version())
                    .orElseGet(() -> PlaceRecommendationTrafficPolicy.create(policy.version(), trafficPercentage));
            savedPolicy.updateTrafficPercentage(trafficPercentage);
            trafficPolicyRepository.save(savedPolicy);
        }

        refreshPolicies();
        return getTrafficPolicies();
    }

    public String getDefaultVersion() {
        return resolvedDefaultVersion;
    }

    public boolean supportsVersion(String version) {
        return StringUtils.hasText(version) && policiesByVersion.containsKey(version.trim());
    }

    private int currentTrafficPercentage(VersionPolicy policy) {
        return trafficOverridesByVersion.getOrDefault(policy.version(), policy.trafficPercentage());
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
                        new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                        new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d),
                        new RankingWeights(0.48d, 0.0d, 0.16d, 0.10d, 0.08d, 0.12d, 0.09d)
                ),
                new VersionPolicy(
                        "place-rec-v2",
                        RecommendationStage.EXPERIMENTAL,
                        0,
                        true,
                        5,
                        0.70d,
                        new CandidateMix(0.40d, 0.20d, 0.25d, 0.15d),
                        new RankingWeights(0.28d, 0.32d, 0.11d, 0.08d, 0.09d, 0.06d, 0.06d),
                        new RankingWeights(0.40d, 0.0d, 0.14d, 0.10d, 0.10d, 0.16d, 0.10d)
                )
        );
    }

    public record ResolvedRecommendationPolicy(
            String version,
            RecommendationStage stage,
            boolean featureLoggingEnabled,
            int portfolioSizeMultiplier,
            double mmrRelevanceWeight,
            PlaceRecommendationProperties.CandidateMix mix,
            PlaceRecommendationProperties.RankingWeights personalizedWeights,
            PlaceRecommendationProperties.RankingWeights anonymousWeights
    ) {
        private static ResolvedRecommendationPolicy from(VersionPolicy policy) {
            return new ResolvedRecommendationPolicy(
                    policy.version(),
                    policy.stage() == null ? RecommendationStage.STABLE : policy.stage(),
                    policy.featureLoggingEnabled(),
                    policy.portfolioSizeMultiplier(),
                    policy.mmrRelevanceWeight(),
                    policy.mix(),
                    policy.personalizedWeights(),
                    policy.anonymousWeights()
            );
        }

        public PlaceRecommendationProperties.RankingWeights weights(boolean hasPersonalSignals) {
            return hasPersonalSignals ? personalizedWeights : anonymousWeights;
        }
    }

    public record RecommendationTrafficPolicy(
            String version,
            RecommendationStage stage,
            int trafficPercentage
    ) {
    }
}
