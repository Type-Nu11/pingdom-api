package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationItem;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final Clock RECOMMENDATION_CLOCK = Clock.systemUTC();

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationExposureService placeRecommendationExposureService;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationGraphAffinityService placeRecommendationGraphAffinityService;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;
    private final PlaceRecommendationPolicyService placeRecommendationPolicyService;
    private final PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;
    private final PlaceRecommendationUserSignalLoader placeRecommendationUserSignalLoader;
    private final PlaceRecommendationCandidateCollector placeRecommendationCandidateCollector;
    private final PlaceRecommendationAggregateLoader placeRecommendationAggregateLoader;
    private final PlaceRecommendationScoringService placeRecommendationScoringService;
    private final PlaceRecommendationPortfolioService placeRecommendationPortfolioService;

    @Override
    @Transactional
    public PlaceRecommendationResponse recommendPlaces(
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
        String recommendationRequestId = UUID.randomUUID().toString();

        UserSignalContext signalContext = placeRecommendationUserSignalLoader.loadUserSignals(userId);
        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                latitude,
                longitude,
                signalContext
        );

        if (candidatePool.isEmpty()) {
            return PlaceRecommendationResponse.of(
                    List.of(),
                    resolvedPolicy.version(),
                    recommendationRequestId,
                    safeLimit,
                    safeRadiusKm,
                    safeRadiusKm
            );
        }

        Map<Long, MapPlace> placeIndex = buildPlaceIndex(candidatePool, signalContext.interactedPlaceIds());
        List<PlaceDistance> placeDistances = candidatePool.stream()
                .map(candidate -> new PlaceDistance(candidate.place(), candidate.sources(), calculateDistanceMeters(
                        latitude,
                        longitude,
                        candidate.place().getLatitude(),
                        candidate.place().getLongitude()
                )))
                .toList();

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
            return PlaceRecommendationResponse.of(
                    List.of(),
                    resolvedPolicy.version(),
                    recommendationRequestId,
                    safeLimit,
                    safeRadiusKm,
                    selection.appliedRadiusKm()
            );
        }

        Map<Long, PlaceAggregate> aggregateMap = placeRecommendationAggregateLoader.loadAggregates(selection.candidates());
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
        List<ScoredCandidate> portfolioCandidates = placeRecommendationPortfolioService.buildCandidatePortfolio(
                safeLimit,
                resolvedPolicy,
                scoredCandidates
        );
        List<ScoredCandidate> rerankedCandidates = placeRecommendationPortfolioService.rerankWithMmr(
                portfolioCandidates,
                safeLimit,
                similarityContext,
                resolvedPolicy.mmrRelevanceWeight()
        );

        List<PlaceRecommendationItem> places = rerankedCandidates.stream()
                .map(candidate -> new PlaceRecommendationItem(
                        candidate.place().getId(),
                        candidate.place().getName(),
                        candidate.place().getAddress(),
                        candidate.place().getLatitude(),
                        candidate.place().getLongitude(),
                        Math.round(candidate.distanceMeters()),
                        buildReason(candidate, hasPersonalSignals),
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

        placeRecommendationExposureService.recordExposures(
                userId,
                latitude,
                longitude,
                recommendationRequestId,
                rerankedCandidates.stream()
                        .map(candidate -> candidate.place().getId())
                        .toList(),
                resolvedPolicy.version()
        );

        return PlaceRecommendationResponse.of(
                places,
                resolvedPolicy.version(),
                recommendationRequestId,
                safeLimit,
                safeRadiusKm,
                appliedRadiusKm
        );
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
            placeIndex.put(seedPlace.getId(), seedPlace);
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
        List<PlaceDistance> sourceBoostedCandidates = placeDistances.stream()
                .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                .filter(candidate -> candidate.sources().contains(CandidateSource.PERSONAL)
                        || candidate.sources().contains(CandidateSource.TREND))
                .sorted(sourceBoostedCandidateComparator())
                .toList();

        for (double radiusStepKm : buildRadiusSteps(requestedRadiusKm)) {
            appliedRadiusKm = radiusStepKm;
            double radiusMeters = radiusStepKm * 1_000d;

            LinkedHashMap<Long, PlaceDistance> selectedCandidates = new LinkedHashMap<>();

            for (PlaceDistance sourceBoostedCandidate : sourceBoostedCandidates) {
                selectedCandidates.put(sourceBoostedCandidate.place().getId(), sourceBoostedCandidate);
                if (selectedCandidates.size() >= targetCandidateCount) {
                    break;
                }
            }

            placeDistances.stream()
                    .filter(candidate -> candidate.sources().contains(CandidateSource.GEO))
                    .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .sorted(Comparator.comparingDouble(PlaceDistance::distanceMeters))
                    .forEach(candidate -> {
                        if (selectedCandidates.size() < targetCandidateCount) {
                            selectedCandidates.putIfAbsent(candidate.place().getId(), candidate);
                        }
                    });

            candidates = List.copyOf(selectedCandidates.values());

            if (candidates.size() >= limit && candidates.size() >= Math.min(targetCandidateCount, limit * 2)) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            List<PlaceDistance> fallbackCandidates = placeDistances.stream()
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .sorted(sourceBoostedCandidateComparator()
                            .thenComparingDouble(PlaceDistance::distanceMeters))
                    .limit(targetCandidateCount)
                    .toList();

            if (!fallbackCandidates.isEmpty()) {
                appliedRadiusKm = Math.max(
                        appliedRadiusKm,
                        fallbackCandidates.get(fallbackCandidates.size() - 1).distanceMeters() / 1_000d
                );
                candidates = fallbackCandidates;
            }
        }

        return new CandidateSelection(candidates, appliedRadiusKm);
    }

    private Comparator<PlaceDistance> sourceBoostedCandidateComparator() {
        return Comparator
                .comparingInt((PlaceDistance candidate) -> candidate.sources().contains(CandidateSource.PERSONAL) ? 0
                        : candidate.sources().contains(CandidateSource.TREND) ? 1 : 2)
                .thenComparingDouble(PlaceDistance::distanceMeters)
                .thenComparing(candidate -> candidate.place().getId());
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
