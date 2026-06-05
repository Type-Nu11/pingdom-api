package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationItem;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
    private static final double FRESHNESS_DECAY_DAYS = 14d;
    private static final double BAYESIAN_PRIOR_WEIGHT = 3d;
    private static final double MMR_RELEVANCE_WEIGHT = 0.75d;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Override
    public PlaceRecommendationResponse recommendPlaces(
            Long userId,
            double latitude,
            double longitude,
            int limit,
            double radiusKm
    ) {
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        double safeRadiusKm = Math.max(MIN_RADIUS_KM, Math.min(radiusKm, MAX_RADIUS_KM));

        List<MapPlace> allPlaces = mapPlaceRepository.findAll().stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .toList();

        if (allPlaces.isEmpty()) {
            return PlaceRecommendationResponse.of(List.of(), safeLimit, safeRadiusKm, safeRadiusKm);
        }

        Map<Long, MapPlace> placeIndex = allPlaces.stream()
                .collect(java.util.stream.Collectors.toMap(MapPlace::getId, place -> place));

        UserSignalContext signalContext = loadUserSignals(userId);
        List<PlaceDistance> placeDistances = allPlaces.stream()
                .map(place -> new PlaceDistance(place, calculateDistanceMeters(
                        latitude,
                        longitude,
                        place.getLatitude(),
                        place.getLongitude()
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
            return PlaceRecommendationResponse.of(List.of(), safeLimit, safeRadiusKm, selection.appliedRadiusKm());
        }

        Map<Long, PlaceAggregate> aggregateMap = loadAggregates(selection.candidates());
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext = buildSimilarityContext(
                selection.candidates(),
                signalContext.interactedPlaceIds(),
                placeIndex
        );
        double globalAverageLikePerPhoto = calculateGlobalAverageLikePerPhoto(selection.candidates(), aggregateMap);
        double maxSeedWeight = signalContext.seedWeights().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0d);
        boolean hasPersonalSignals = !signalContext.seedWeights().isEmpty();
        double appliedRadiusKm = selection.appliedRadiusKm();

        List<IntermediateCandidate> intermediateCandidates = selection.candidates().stream()
                .map(candidate -> toIntermediateCandidate(
                        candidate,
                        aggregateMap.getOrDefault(candidate.place().getId(), PlaceAggregate.empty()),
                        signalContext,
                        globalAverageLikePerPhoto,
                        maxSeedWeight,
                        appliedRadiusKm,
                        similarityContext
                ))
                .toList();

        List<ScoredCandidate> scoredCandidates = rerankWithMmr(
                applyFinalScores(intermediateCandidates, hasPersonalSignals),
                safeLimit,
                similarityContext
        );

        List<PlaceRecommendationItem> places = scoredCandidates.stream()
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

        return PlaceRecommendationResponse.of(places, safeLimit, safeRadiusKm, appliedRadiusKm);
    }

    private CandidateSelection selectCandidates(
            List<PlaceDistance> placeDistances,
            int limit,
            double requestedRadiusKm,
            Set<Long> excludedPlaceIds
    ) {
        double appliedRadiusKm = requestedRadiusKm;
        List<PlaceDistance> candidates = List.of();

        for (double radiusStepKm : buildRadiusSteps(requestedRadiusKm)) {
            appliedRadiusKm = radiusStepKm;
            double radiusMeters = radiusStepKm * 1_000d;

            candidates = placeDistances.stream()
                    .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .toList();

            if (candidates.size() >= limit) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            List<PlaceDistance> fallbackCandidates = placeDistances.stream()
                    .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                    .sorted(Comparator.comparingDouble(PlaceDistance::distanceMeters))
                    .limit(Math.max(limit * 3L, limit))
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

    private UserSignalContext loadUserSignals(Long userId) {
        if (userId == null) {
            return UserSignalContext.empty();
        }

        Map<Long, Double> seedWeights = new HashMap<>();
        Map<Long, PersonalSignalType> signalTypes = new HashMap<>();

        registerSignals(
                mapBookmarkRepository.findPlaceIdsByUserId(userId),
                1.0d,
                PersonalSignalType.BOOKMARK,
                seedWeights,
                signalTypes
        );
        registerSignals(
                mapImageLikeRepository.findPlaceIdsByUserId(userId),
                0.6d,
                PersonalSignalType.LIKE,
                seedWeights,
                signalTypes
        );
        registerSignals(
                mapImageRepository.findPlaceIdsByUserId(userId),
                0.3d,
                PersonalSignalType.UPLOAD,
                seedWeights,
                signalTypes
        );

        return new UserSignalContext(seedWeights, signalTypes, Set.copyOf(seedWeights.keySet()));
    }

    private void registerSignals(
            Collection<Long> placeIds,
            double weight,
            PersonalSignalType signalType,
            Map<Long, Double> seedWeights,
            Map<Long, PersonalSignalType> signalTypes
    ) {
        for (Long placeId : placeIds) {
            seedWeights.merge(placeId, weight, Double::sum);
            signalTypes.merge(placeId, signalType, PersonalSignalType::stronger);
        }
    }

    private Map<Long, PlaceAggregate> loadAggregates(List<PlaceDistance> candidates) {
        List<Long> placeIds = candidates.stream()
                .map(candidate -> candidate.place().getId())
                .toList();

        Map<Long, PlaceAggregate> aggregateMap = new HashMap<>();

        for (MapBookmarkRepository.PlaceBookmarkCountProjection projection :
                mapBookmarkRepository.findBookmarkCountsByPlaceIds(placeIds)) {
            aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> PlaceAggregate.empty())
                    .bookmarkCount = projection.getBookmarkCount();
        }

        for (MapImageRepository.PlaceImageAggregateProjection projection :
                mapImageRepository.findPlaceAggregatesByPlaceIds(placeIds)) {
            aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> PlaceAggregate.empty())
                    .mergeImageAggregate(projection.getLikeSum(), projection.getLatestCreatedAt());
        }

        return aggregateMap;
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

    private double calculateGlobalAverageLikePerPhoto(
            List<PlaceDistance> candidates,
            Map<Long, PlaceAggregate> aggregateMap
    ) {
        return candidates.stream()
                .map(candidate -> {
                    PlaceAggregate aggregate = aggregateMap.getOrDefault(candidate.place().getId(), PlaceAggregate.empty());
                    long photoCount = candidate.place().currentPhotoCount();
                    if (photoCount <= 0L) {
                        return 0d;
                    }
                    return (double) aggregate.likeSum / (double) photoCount;
                })
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
    }

    private IntermediateCandidate toIntermediateCandidate(
            PlaceDistance candidate,
            PlaceAggregate aggregate,
            UserSignalContext signalContext,
            double globalAverageLikePerPhoto,
            double maxSeedWeight,
            double appliedRadiusKm,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        double personalScore = 0d;
        PersonalSignalType dominantSignalType = PersonalSignalType.NONE;

        for (Map.Entry<Long, Double> seedWeightEntry : signalContext.seedWeights().entrySet()) {
            double similarity = placeRecommendationSimilarityService.similarity(
                    candidate.place().getId(),
                    seedWeightEntry.getKey(),
                    similarityContext
            );
            double normalizedSeedWeight = seedWeightEntry.getValue() / maxSeedWeight;
            double contribution = normalizedSeedWeight * similarity;

            if (contribution > personalScore) {
                personalScore = contribution;
                dominantSignalType = signalContext.signalTypes().getOrDefault(seedWeightEntry.getKey(), PersonalSignalType.NONE);
            }
        }

        double geoScore = 1d - Math.min(candidate.distanceMeters() / 1_000d / appliedRadiusKm, 1d);

        long photoCount = candidate.place().currentPhotoCount();
        double smoothedLikeAverage = (aggregate.likeSum + BAYESIAN_PRIOR_WEIGHT * globalAverageLikePerPhoto)
                / (photoCount + BAYESIAN_PRIOR_WEIGHT);
        double rawQualityScore = smoothedLikeAverage
                + (Math.log1p(aggregate.bookmarkCount) * 0.35d)
                + (Math.log1p(photoCount) * 0.20d);
        double freshnessScore = calculateFreshnessScore(aggregate.latestCreatedAt);

        return new IntermediateCandidate(
                candidate.place(),
                candidate.distanceMeters(),
                geoScore,
                personalScore,
                rawQualityScore,
                freshnessScore,
                dominantSignalType
        );
    }

    private List<ScoredCandidate> rerankWithMmr(
            List<ScoredCandidate> candidates,
            int limit,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        List<ScoredCandidate> remaining = new ArrayList<>(candidates);
        List<ScoredCandidate> selected = new ArrayList<>();

        // MMR로 지나치게 비슷한 후보가 연속 노출되는 것을 줄인다.
        while (!remaining.isEmpty() && selected.size() < limit) {
            ScoredCandidate next = remaining.stream()
                    .max(Comparator
                            .comparingDouble((ScoredCandidate candidate) -> mmrScore(candidate, selected, similarityContext))
                            .thenComparing(baseScoreComparator()))
                    .orElseThrow();

            selected.add(next);
            remaining.remove(next);
        }

        return List.copyOf(selected);
    }

    private Comparator<ScoredCandidate> baseScoreComparator() {
        return Comparator
                .comparingDouble(ScoredCandidate::finalScore)
                .thenComparingDouble(ScoredCandidate::personalScore)
                .thenComparingDouble(ScoredCandidate::qualityScore)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::distanceMeters).reversed())
                .thenComparing(candidate -> candidate.place().getId());
    }

    private double mmrScore(
            ScoredCandidate candidate,
            List<ScoredCandidate> selected,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        if (selected.isEmpty()) {
            return candidate.finalScore();
        }

        double maxSimilarityToSelected = selected.stream()
                .mapToDouble(selectedCandidate -> placeRecommendationSimilarityService.similarity(
                        candidate.place().getId(),
                        selectedCandidate.place().getId(),
                        similarityContext
                ))
                .max()
                .orElse(0d);

        return (MMR_RELEVANCE_WEIGHT * candidate.finalScore())
                - ((1d - MMR_RELEVANCE_WEIGHT) * maxSimilarityToSelected);
    }

    private List<ScoredCandidate> applyFinalScores(List<IntermediateCandidate> candidates, boolean hasPersonalSignals) {
        double minQuality = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawQualityScore)
                .min()
                .orElse(0d);
        double maxQuality = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawQualityScore)
                .max()
                .orElse(0d);

        return candidates.stream()
                .map(candidate -> {
                    double normalizedQuality = normalize(candidate.rawQualityScore(), minQuality, maxQuality);
                    double finalScore = hasPersonalSignals
                            ? (0.35d * candidate.geoScore())
                            + (0.35d * candidate.personalScore())
                            + (0.20d * normalizedQuality)
                            + (0.10d * candidate.freshnessScore())
                            : (0.55d * candidate.geoScore())
                            + (0.30d * normalizedQuality)
                            + (0.15d * candidate.freshnessScore());

                    return new ScoredCandidate(
                            candidate.place(),
                            candidate.distanceMeters(),
                            candidate.geoScore(),
                            candidate.personalScore(),
                            normalizedQuality,
                            candidate.freshnessScore(),
                            candidate.dominantSignalType(),
                            finalScore
                    );
                })
                .toList();
    }

    private double calculateFreshnessScore(LocalDateTime latestCreatedAt) {
        if (latestCreatedAt == null) {
            return 0d;
        }

        double days = Math.max(
                0d,
                Duration.between(latestCreatedAt, LocalDateTime.now()).toHours() / 24d
        );
        return Math.exp(-days / FRESHNESS_DECAY_DAYS);
    }

    private double normalize(double value, double min, double max) {
        if (Double.compare(min, max) == 0) {
            return max > 0d ? 0.5d : 0d;
        }
        return (value - min) / (max - min);
    }

    private String buildReason(ScoredCandidate candidate, boolean hasPersonalSignals) {
        if (hasPersonalSignals
                && candidate.personalScore() >= 0.25d
                && candidate.dominantSignalType() != PersonalSignalType.NONE) {
            return switch (candidate.dominantSignalType()) {
                case BOOKMARK -> "저장한 장소와 가까운 추천 장소입니다.";
                case LIKE -> "좋아요한 장소와 가까운 추천 장소입니다.";
                case UPLOAD -> "업로드한 장소와 가까운 추천 장소입니다.";
                case NONE -> "회원님의 반응 이력과 가까운 장소입니다.";
            };
        }

        if (candidate.freshnessScore() >= 0.60d) {
            return "현재 위치 주변에서 최근 업로드가 활발한 장소입니다.";
        }

        if (candidate.qualityScore() >= 0.45d) {
            return "현재 위치 주변에서 반응이 좋은 장소입니다.";
        }

        return "현재 위치와 가까운 장소입니다.";
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

    private enum PersonalSignalType {
        BOOKMARK(3),
        LIKE(2),
        UPLOAD(1),
        NONE(0);

        private final int priority;

        PersonalSignalType(int priority) {
            this.priority = priority;
        }

        private static PersonalSignalType stronger(PersonalSignalType left, PersonalSignalType right) {
            return left.priority >= right.priority ? left : right;
        }
    }

    private record UserSignalContext(
            Map<Long, Double> seedWeights,
            Map<Long, PersonalSignalType> signalTypes,
            Set<Long> interactedPlaceIds
    ) {
        private static UserSignalContext empty() {
            return new UserSignalContext(Map.of(), Map.of(), Set.of());
        }
    }

    private record PlaceDistance(MapPlace place, double distanceMeters) {
    }

    private record CandidateSelection(List<PlaceDistance> candidates, double appliedRadiusKm) {
    }

    private record IntermediateCandidate(
            MapPlace place,
            double distanceMeters,
            double geoScore,
            double personalScore,
            double rawQualityScore,
            double freshnessScore,
            PersonalSignalType dominantSignalType
    ) {
    }

    private record ScoredCandidate(
            MapPlace place,
            double distanceMeters,
            double geoScore,
            double personalScore,
            double qualityScore,
            double freshnessScore,
            PersonalSignalType dominantSignalType,
            double finalScore
    ) {
    }

    private static final class PlaceAggregate {
        private long bookmarkCount;
        private long likeSum;
        private LocalDateTime latestCreatedAt;

        private static PlaceAggregate empty() {
            return new PlaceAggregate();
        }

        private void mergeImageAggregate(Long likeSum, LocalDateTime latestCreatedAt) {
            this.likeSum = likeSum == null ? 0L : likeSum;
            this.latestCreatedAt = latestCreatedAt;
        }
    }
}
