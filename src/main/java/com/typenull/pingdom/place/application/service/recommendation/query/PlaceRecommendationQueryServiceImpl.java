package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.policy.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationItem;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.explanation.PlaceRecommendationLimitReason;
import com.typenull.pingdom.place.domain.recommendation.explanation.PlaceRecommendationReason;
import com.typenull.pingdom.place.event.PlaceRecommendationExposureRecordRequestedEvent;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 신호, 후보 원천, 정책 가중치와 운영 상태를 결합해 추천 응답과 관측 기록 요청을
 * 함께 조립하는 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationQueryServiceImpl implements PlaceRecommendationQueryService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;
    private static final double MIN_RADIUS_KM = 1.0d;
    private static final double MAX_RADIUS_KM = 20.0d;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final int CANDIDATE_POOL_LIMIT = 300;
    private static final int MIN_SELECTION_POOL_SIZE = 40;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationGraphAffinityService placeRecommendationGraphAffinityService;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;
    private final PlaceRecommendationPolicyService placeRecommendationPolicyService;
    private final PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;
    private final PlaceRecommendationUserSignalLoader placeRecommendationUserSignalLoader;
    private final PlaceRecommendationCandidateCollector placeRecommendationCandidateCollector;
    private final PlaceRecommendationAggregateLoader placeRecommendationAggregateLoader;
    private final PlaceRecommendationTrustScoreLoader placeRecommendationTrustScoreLoader;
    private final PlaceRecommendationScoringService placeRecommendationScoringService;
    private final KCultureInterestRankingService kCultureInterestRankingService;
    private final CurrentActivityIntentRankingService currentActivityIntentRankingService;
    private final PlaceRecommendationCommerceSignalLoader placeRecommendationCommerceSignalLoader;
    private final PlaceRecommendationCommerceRankingService placeRecommendationCommerceRankingService;
    private final VerifiedBoostRankingService verifiedBoostRankingService;
    private final PlaceRecommendationPortfolioService placeRecommendationPortfolioService;
    private final RecommendationMetrics recommendationMetrics;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 추천 응답을 조합하고, 활성화된 feature log는 같은 쓰기 트랜잭션에 저장한 뒤 노출 기록을
     * 커밋 후 처리하도록 요청합니다. 노출 기록 실패는 추천 응답을 실패시키지 않습니다.
     */
    @Override
    @Transactional
    public PlaceRecommendationResponse recommendAndRecordObservations(
            Long userId,
            double latitude,
            double longitude,
            int limit,
            double radiusKm,
            String requestedRecommendationVersion
    ) {
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        double safeRadiusKm = Math.max(MIN_RADIUS_KM, Math.min(radiusKm, MAX_RADIUS_KM));
        PlaceRecommendationPolicyService.ResolvedRecommendationPolicy resolvedPolicy =
                placeRecommendationPolicyService.resolve(userId, latitude, longitude, requestedRecommendationVersion);
        if (resolvedPolicy.sourceVersion() != null
                && !resolvedPolicy.sourceVersion().equals(resolvedPolicy.version())
                && resolvedPolicy.fallbackReason() != null) {
            recommendationMetrics.recordKillSwitchFallback(
                    resolvedPolicy.sourceVersion(),
                    resolvedPolicy.version(),
                    resolvedPolicy.fallbackReason()
            );
        }
        String recommendationRequestId = UUID.randomUUID().toString();

        UserSignalContext signalContext = placeRecommendationUserSignalLoader.loadUserSignals(userId);
        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                latitude,
                longitude,
                signalContext
        );

        if (candidatePool.isEmpty()) {
            return recordRecommendationRequest(PlaceRecommendationResponse.of(
                    List.of(),
                    resolvedPolicy.version(),
                    recommendationRequestId,
                    safeLimit,
                    safeRadiusKm,
                    safeRadiusKm,
                    Set.of(),
                    null,
                    resolveLimitReasons(limit, safeLimit, radiusKm, safeRadiusKm,
                            signalContext.interactedPlaceIds(), false, List.of())
            ));
        }

        Map<Long, MapPlace> placeIndex = buildPlaceIndex(candidatePool, signalContext.interactedPlaceIds());
        List<PlaceDistance> placeDistances = candidatePool.stream()
                .map(candidate -> new PlaceDistance(
                        candidate.place(),
                        candidate.sources(),
                        calculateDistanceMeters(
                                latitude,
                                longitude,
                                candidate.place().getLatitude(),
                                candidate.place().getLongitude()
                        ),
                        candidate.currentlyOperating(),
                        candidate.currentlyOperatingCheckedAt()
                ))
                .toList();
        Map<Long, CandidatePlace> candidateStateByPlaceId = candidatePool.stream()
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.place().getId(),
                        candidate -> candidate
                ));

        CandidateSelection selection = selectCandidates(
                placeDistances,
                safeLimit,
                safeRadiusKm,
                signalContext.interactedPlaceIds()
        );

        if (selection.candidates().isEmpty() && !signalContext.interactedPlaceIds().isEmpty()) {
            selection = selectCandidates(placeDistances, safeLimit, safeRadiusKm, Set.of());
        }

        if (selection.candidates().isEmpty()) {
            return recordRecommendationRequest(PlaceRecommendationResponse.of(
                    List.of(),
                    resolvedPolicy.version(),
                    recommendationRequestId,
                    safeLimit,
                    safeRadiusKm,
                    selection.appliedRadiusKm(),
                    Set.of(),
                    null,
                    resolveLimitReasons(limit, safeLimit, radiusKm, selection.appliedRadiusKm(),
                            signalContext.interactedPlaceIds(), selection.fallbackCandidatePool(), selection.candidates())
            ));
        }

        Map<Long, PlaceAggregate> aggregateMap = placeRecommendationAggregateLoader.loadAggregates(selection.candidates());
        Map<Long, Double> trustScores = placeRecommendationTrustScoreLoader.load(selection.candidates());
        long totalClickCount = placeRecommendationAggregateLoader.resolveTotalClickCount();
        long totalExposureCount = placeRecommendationAggregateLoader.resolveTotalExposureCount();
        final long resolvedTotalExposureCount = totalExposureCount;
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext = buildSimilarityContext(
                selection.candidates(),
                signalContext.interactedPlaceIds(),
                placeIndex
        );
        Map<Long, Double> graphAffinityScores = placeRecommendationGraphAffinityService.score(
                selection.candidates().stream()
                        .map(candidate -> candidate.place().getId())
                        .toList(),
                signalContext.seedWeights(),
                similarityContext
        );
        PlaceRecommendationScoringService.RecommendationScoreContext scoreContext =
                placeRecommendationScoringService.buildScoreContext(
                        selection.candidates(),
                        aggregateMap,
                        totalClickCount,
                        totalExposureCount
                );
        double maxSeedWeight = signalContext.seedWeights().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0d);
        boolean hasPersonalSignals = !signalContext.seedWeights().isEmpty();
        double appliedRadiusKm = selection.appliedRadiusKm();

        List<IntermediateCandidate> intermediateCandidates = placeRecommendationScoringService.buildIntermediateCandidates(
                selection.candidates(),
                aggregateMap,
                signalContext,
                graphAffinityScores,
                trustScores,
                resolvedTotalExposureCount,
                maxSeedWeight,
                appliedRadiusKm,
                similarityContext,
                scoreContext
        );

        List<ScoredCandidate> scoredCandidates = placeRecommendationScoringService.applyFinalScores(
                intermediateCandidates,
                resolvedPolicy.weights(hasPersonalSignals)
        );
        Map<Long, PlaceRecommendationCommerceSignalLoader.CommerceSignal> commerceSignalsByPlaceId =
                placeRecommendationCommerceSignalLoader.load(selection.candidates().stream()
                        .map(candidate -> candidate.place().getId())
                        .toList());
        KCultureInterestRankingService.InterestRankingResult interestRankingResult =
                kCultureInterestRankingService.apply(
                        userId,
                        scoredCandidates,
                        resolvedPolicy.interestMatchBoost()
                );
        CurrentActivityIntentRankingService.IntentRankingResult intentRankingResult =
                currentActivityIntentRankingService.apply(
                        userId,
                        interestRankingResult.candidates(),
                        resolvedPolicy.intentMatchBoost(),
                        interestRankingResult.categoriesByPlaceId()
                );
        List<ScoredCandidate> commerceRankedCandidates = placeRecommendationCommerceRankingService.apply(
                intentRankingResult.candidates(),
                commerceSignalsByPlaceId,
                resolvedPolicy.benefitBoost(),
                resolvedPolicy.availabilityBoost()
        );
        VerifiedBoostRankingService.RankingResult boostRankingResult = verifiedBoostRankingService.apply(
                commerceRankedCandidates
        );
        List<ScoredCandidate> rerankedCandidates = selectOperationallyPrioritizedCandidates(
                boostRankingResult.candidates(),
                safeLimit,
                resolvedPolicy,
                similarityContext,
                candidateStateByPlaceId
        );

        List<PlaceRecommendationItem> places = rerankedCandidates.stream()
                .map(candidate -> new PlaceRecommendationItem(
                        candidate.place().getId(),
                        candidate.place().getName(),
                        candidate.place().getAddress(),
                        candidate.place().getRoadAddress(),
                        candidate.place().getJibunAddress(),
                        candidate.place().getPostalCode(),
                        candidate.place().getGeocodingSource(),
                        candidate.place().getOperatingStatus(),
                        candidate.place().getOperatingStatusCheckedAt(),
                        candidateStateByPlaceId.get(candidate.place().getId()).currentlyOperating(),
                        candidateStateByPlaceId.get(candidate.place().getId()).currentlyOperatingCheckedAt(),
                        candidate.place().getLatitude(),
                        candidate.place().getLongitude(),
                        Math.round(candidate.distanceMeters()),
                        buildReason(candidate, hasPersonalSignals),
                        resolveReason(candidate, hasPersonalSignals),
                        commerceSignalsByPlaceId.getOrDefault(
                                candidate.place().getId(),
                                PlaceRecommendationCommerceSignalLoader.CommerceSignal.NONE
                        ).activeBenefit(),
                        commerceSignalsByPlaceId.getOrDefault(
                                candidate.place().getId(),
                                PlaceRecommendationCommerceSignalLoader.CommerceSignal.NONE
                        ).reservable(),
                        boostRankingResult.boostedPlaceIds().contains(candidate.place().getId()),
                        placeGrowthService.snapshot(candidate.place())
                ))
                .toList();

        if (resolvedPolicy.featureLoggingEnabled()) {
            placeRecommendationFeatureLogService.recordShownCandidates(
                    recommendationRequestId,
                    userId,
                    resolvedPolicy.version(),
                    resolvedPolicy.stage(),
                    placeRecommendationPortfolioService.toFeatureRecords(rerankedCandidates)
            );
        }

        eventPublisher.publishEvent(new PlaceRecommendationExposureRecordRequestedEvent(
                userId,
                latitude,
                longitude,
                recommendationRequestId,
                rerankedCandidates.stream()
                        .map(candidate -> candidate.place().getId())
                        .toList(),
                resolvedPolicy.version()
        ));

        return recordRecommendationRequest(PlaceRecommendationResponse.of(
                places,
                resolvedPolicy.version(),
                recommendationRequestId,
                safeLimit,
                safeRadiusKm,
                appliedRadiusKm,
                interestRankingResult.interests(),
                intentRankingResult.intent(),
                resolveLimitReasons(limit, safeLimit, radiusKm, appliedRadiusKm,
                        signalContext.interactedPlaceIds(), selection.fallbackCandidatePool(), selection.candidates(),
                        rerankedCandidates, candidateStateByPlaceId)
        ));
    }

    private List<ScoredCandidate> selectOperationallyPrioritizedCandidates(
            List<ScoredCandidate> candidates,
            int limit,
            PlaceRecommendationPolicyService.ResolvedRecommendationPolicy resolvedPolicy,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            Map<Long, CandidatePlace> candidateStateByPlaceId
    ) {
        List<ScoredCandidate> selected = new ArrayList<>();
        for (int priority = 0; priority <= 2 && selected.size() < limit; priority++) {
            int currentPriority = priority;
            List<ScoredCandidate> tierCandidates = candidates.stream()
                    .filter(candidate -> operatingPriority(candidateStateByPlaceId
                            .get(candidate.place().getId())
                            .currentlyOperating()) == currentPriority)
                    .toList();
            if (tierCandidates.isEmpty()) {
                continue;
            }

            int remainingLimit = limit - selected.size();
            List<ScoredCandidate> portfolioCandidates = placeRecommendationPortfolioService.buildCandidatePortfolio(
                    remainingLimit,
                    resolvedPolicy,
                    tierCandidates
            );
            selected.addAll(placeRecommendationPortfolioService.rerankWithMmr(
                    portfolioCandidates,
                    remainingLimit,
                    similarityContext,
                    resolvedPolicy.mmrRelevanceWeight()
            ));
        }
        return List.copyOf(selected);
    }

    private PlaceRecommendationResponse recordRecommendationRequest(PlaceRecommendationResponse response) {
        recommendationMetrics.recordRequest(response.recommendationVersion(), response.recommendedCount());
        return response;
    }

    private Map<Long, MapPlace> buildPlaceIndex(List<CandidatePlace> candidatePool, Set<Long> interactedPlaceIds) {
        Map<Long, MapPlace> placeIndex = new HashMap<>();
        for (CandidatePlace candidate : candidatePool) {
            placeIndex.put(candidate.place().getId(), candidate.place());
        }

        if (interactedPlaceIds.isEmpty()) {
            return placeIndex;
        }

        Set<Long> missingPlaceIds = new HashSet<>(interactedPlaceIds);
        missingPlaceIds.removeAll(placeIndex.keySet());

        if (missingPlaceIds.isEmpty()) {
            return placeIndex;
        }

        for (MapPlace seedPlace : mapPlaceRepository.findAllById(missingPlaceIds)) {
            if (seedPlace.isOperating()) {
                placeIndex.put(seedPlace.getId(), seedPlace);
            }
        }

        return placeIndex;
    }

    private CandidateSelection selectCandidates(
            List<PlaceDistance> placeDistances,
            int limit,
            double requestedRadiusKm,
            Set<Long> excludedPlaceIds
    ) {
        int targetCandidateCount = Math.min(
                CANDIDATE_POOL_LIMIT,
                Math.max(MIN_SELECTION_POOL_SIZE, limit * 4)
        );
        double appliedRadiusKm = requestedRadiusKm;
        List<PlaceDistance> candidates = List.of();
        for (double radiusStepKm : buildRadiusSteps(requestedRadiusKm)) {
            appliedRadiusKm = radiusStepKm;
            double radiusMeters = radiusStepKm * 1_000d;

            candidates = placeDistances.stream()
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .filter(candidate -> candidate.sources().contains(CandidateSource.PERSONAL)
                            || candidate.sources().contains(CandidateSource.TREND)
                            || (candidate.sources().contains(CandidateSource.GEO)
                            && candidate.distanceMeters() <= radiusMeters))
                    .sorted(operatingCandidateComparator()
                            .thenComparing(sourceBoostedCandidateComparator()))
                    .limit(targetCandidateCount)
                    .toList();

            if (candidates.size() >= limit && candidates.size() >= Math.min(targetCandidateCount, limit * 2)) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            List<PlaceDistance> fallbackCandidates = placeDistances.stream()
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .sorted(operatingCandidateComparator()
                            .thenComparing(sourceBoostedCandidateComparator())
                            .thenComparingDouble(PlaceDistance::distanceMeters))
                    .limit(targetCandidateCount)
                    .toList();

            if (!fallbackCandidates.isEmpty()) {
                appliedRadiusKm = Math.max(
                        appliedRadiusKm,
                        fallbackCandidates.get(fallbackCandidates.size() - 1).distanceMeters() / 1_000d
                );
                candidates = fallbackCandidates;
                return new CandidateSelection(candidates, appliedRadiusKm, true);
            }
        }

        return new CandidateSelection(candidates, appliedRadiusKm, false);
    }

    private Comparator<PlaceDistance> sourceBoostedCandidateComparator() {
        return Comparator
                .comparingInt((PlaceDistance candidate) -> candidate.sources().contains(CandidateSource.PERSONAL) ? 0
                        : candidate.sources().contains(CandidateSource.TREND) ? 1 : 2)
                .thenComparingDouble(PlaceDistance::distanceMeters)
                .thenComparing(candidate -> candidate.place().getId());
    }

    private Comparator<PlaceDistance> operatingCandidateComparator() {
        return Comparator.comparingInt(candidate -> operatingPriority(candidate.currentlyOperating()));
    }

    private int operatingPriority(Boolean currentlyOperating) {
        if (Boolean.TRUE.equals(currentlyOperating)) {
            return 0;
        }
        return currentlyOperating == null ? 1 : 2;
    }

    private List<Double> buildRadiusSteps(double requestedRadiusKm) {
        Set<Double> radiusSteps = new LinkedHashSet<>();
        double current = requestedRadiusKm;
        radiusSteps.add(current);

        while (current < MAX_RADIUS_KM) {
            current = Math.min(MAX_RADIUS_KM, current * 2d);
            radiusSteps.add(current);
        }

        return new ArrayList<>(radiusSteps);
    }

    private PlaceRecommendationSimilarityService.SimilarityContext buildSimilarityContext(
            List<PlaceDistance> candidates,
            Set<Long> interactedPlaceIds,
            Map<Long, MapPlace> placeIndex
    ) {
        Set<Long> relatedPlaceIds = new LinkedHashSet<>(interactedPlaceIds);
        candidates.stream()
                .map(candidate -> candidate.place().getId())
                .forEach(relatedPlaceIds::add);

        return placeRecommendationSimilarityService.buildContext(relatedPlaceIds, placeIndex);
    }

    private String buildReason(ScoredCandidate candidate, boolean hasPersonalSignals) {
        return switch (resolveReason(candidate, hasPersonalSignals)) {
            case BENEFIT_AND_RESERVABLE -> "현재 이용 가능한 혜택과 예약이 있는 장소입니다.";
            case ACTIVE_BENEFIT -> "현재 이용 가능한 혜택이 있는 장소입니다.";
            case RESERVABLE -> "현재 예약 가능한 장소입니다.";
            case CONTEXT_MATCH -> "회원님의 K-컬처 관심사와 현재 여행 맥락에 맞는 장소입니다.";
            case PERSONAL_SIGNAL -> switch (candidate.dominantSignalType()) {
                case BOOKMARK -> "저장한 장소와 가까운 추천 장소입니다.";
                case LIKE -> "좋아요한 장소와 가까운 추천 장소입니다.";
                case UPLOAD -> "업로드한 장소와 가까운 추천 장소입니다.";
                case NONE -> "회원님의 반응 이력과 가까운 장소입니다.";
            };
            case FRESH_CONTENT -> "현재 위치 주변에서 최근 업로드가 활발한 장소입니다.";
            case HIGH_ENGAGEMENT -> "현재 위치 주변에서 추천 클릭 반응이 좋은 장소입니다.";
            case HIGH_CONVERSION -> "현재 위치 주변에서 저장 전환 반응이 좋은 장소입니다.";
            case EXPLORATION -> "현재 위치 주변에서 새롭게 탐색 중인 장소입니다.";
            case QUALITY_SIGNAL -> "현재 위치 주변에서 반응이 좋은 장소입니다.";
            case NEARBY -> "현재 위치와 가까운 장소입니다.";
        };
    }

    private PlaceRecommendationReason resolveReason(ScoredCandidate candidate, boolean hasPersonalSignals) {
        if (candidate.benefitScore() > 0d && candidate.availabilityScore() > 0d) {
            return PlaceRecommendationReason.BENEFIT_AND_RESERVABLE;
        }
        if (candidate.benefitScore() > 0d) {
            return PlaceRecommendationReason.ACTIVE_BENEFIT;
        }
        if (candidate.availabilityScore() > 0d) {
            return PlaceRecommendationReason.RESERVABLE;
        }
        if (candidate.contextScore() > 0d) {
            return PlaceRecommendationReason.CONTEXT_MATCH;
        }
        if (hasPersonalSignals
                && candidate.personalScore() >= 0.25d
                && candidate.dominantSignalType() != PersonalSignalType.NONE) {
            return PlaceRecommendationReason.PERSONAL_SIGNAL;
        }
        if (candidate.freshnessScore() >= 0.60d) {
            return PlaceRecommendationReason.FRESH_CONTENT;
        }
        if (candidate.engagementScore() >= 0.60d) {
            return PlaceRecommendationReason.HIGH_ENGAGEMENT;
        }
        if (candidate.conversionScore() >= 0.55d) {
            return PlaceRecommendationReason.HIGH_CONVERSION;
        }
        if (candidate.explorationScore() >= 0.65d && candidate.qualityScore() < 0.45d) {
            return PlaceRecommendationReason.EXPLORATION;
        }
        if (candidate.qualityScore() >= 0.45d) {
            return PlaceRecommendationReason.QUALITY_SIGNAL;
        }
        return PlaceRecommendationReason.NEARBY;
    }

    private List<PlaceRecommendationLimitReason> resolveLimitReasons(
            int requestedLimit,
            int safeLimit,
            double requestedRadiusKm,
            double appliedRadiusKm,
            Set<Long> interactedPlaceIds,
            boolean fallbackCandidatePool,
            List<PlaceDistance> selectedCandidates
    ) {
        EnumSet<PlaceRecommendationLimitReason> reasons = EnumSet.noneOf(PlaceRecommendationLimitReason.class);
        if (requestedLimit != safeLimit) {
            reasons.add(PlaceRecommendationLimitReason.REQUEST_LIMIT_CLAMPED);
        }
        if (appliedRadiusKm > requestedRadiusKm) {
            reasons.add(PlaceRecommendationLimitReason.RADIUS_EXPANDED);
        }
        if (!interactedPlaceIds.isEmpty()) {
            reasons.add(PlaceRecommendationLimitReason.INTERACTED_PLACE_EXCLUDED);
        }
        if (fallbackCandidatePool) {
            reasons.add(PlaceRecommendationLimitReason.FALLBACK_CANDIDATE_POOL);
        }
        if (selectedCandidates.stream().anyMatch(candidate -> !Boolean.TRUE.equals(candidate.currentlyOperating()))) {
            reasons.add(PlaceRecommendationLimitReason.OPERATING_STATUS_PRIORITY);
        }
        return List.copyOf(reasons);
    }

    private List<PlaceRecommendationLimitReason> resolveLimitReasons(
            int requestedLimit,
            int safeLimit,
            double requestedRadiusKm,
            double appliedRadiusKm,
            Set<Long> interactedPlaceIds,
            boolean fallbackCandidatePool,
            List<PlaceDistance> selectedCandidates,
            List<ScoredCandidate> rerankedCandidates,
            Map<Long, CandidatePlace> candidateStateByPlaceId
    ) {
        List<PlaceDistance> outputCandidates = rerankedCandidates.stream()
                .map(candidate -> {
                    CandidatePlace state = candidateStateByPlaceId.get(candidate.place().getId());
                    return new PlaceDistance(
                            candidate.place(),
                            Set.of(),
                            candidate.distanceMeters(),
                            state.currentlyOperating(),
                            state.currentlyOperatingCheckedAt()
                    );
                })
                .toList();
        return resolveLimitReasons(
                requestedLimit,
                safeLimit,
                requestedRadiusKm,
                appliedRadiusKm,
                interactedPlaceIds,
                fallbackCandidatePool,
                selectedCandidates.isEmpty() ? outputCandidates : selectedCandidates
        );
    }

    private double calculateDistanceMeters(
            double baseLatitude,
            double baseLongitude,
            double targetLatitude,
            double targetLongitude
    ) {
        double latitudeDelta = Math.toRadians(targetLatitude - baseLatitude);
        double longitudeDelta = Math.toRadians(targetLongitude - baseLongitude);
        double baseLatitudeRadians = Math.toRadians(baseLatitude);
        double targetLatitudeRadians = Math.toRadians(targetLatitude);

        double a = Math.sin(latitudeDelta / 2d) * Math.sin(latitudeDelta / 2d)
                + Math.cos(baseLatitudeRadians) * Math.cos(targetLatitudeRadians)
                * Math.sin(longitudeDelta / 2d) * Math.sin(longitudeDelta / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }

}
