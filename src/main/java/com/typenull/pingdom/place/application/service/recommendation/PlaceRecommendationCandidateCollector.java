package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PlaceRecommendationCandidateCollector {

    private static final int CANDIDATE_POOL_LIMIT = 300;
    private static final int GEO_CANDIDATE_LIMIT = 180;
    private static final int PERSONAL_CANDIDATE_LIMIT = 120;
    private static final int TREND_CANDIDATE_LIMIT = 80;
    private static final long TREND_LOOKBACK_DAYS = 7L;
    private static final int PERSONAL_EXPANSION_PER_SEED_LIMIT = 30;
    private static final int PERSONAL_EXPANSION_SEED_LIMIT = 5;
    private static final double PERSONAL_EXPANSION_RADIUS_KM = 10.0d;
    private static final double MAX_RADIUS_KM = 20.0d;
    private static final Clock RECOMMENDATION_CLOCK = Clock.systemUTC();

    private final MapPlaceRepository mapPlaceRepository;
    private final MapPlaceRecommendationCandidateRepository mapPlaceRecommendationCandidateRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    List<CandidatePlace> loadCandidatePool(double latitude, double longitude, UserSignalContext signalContext) {
        LinkedHashMap<Long, CandidatePlaceAccumulator> mergedCandidates = new LinkedHashMap<>();

        mergeCandidates(
                mergedCandidates,
                loadGeoCandidates(latitude, longitude, MAX_RADIUS_KM),
                CandidateSource.GEO,
                GEO_CANDIDATE_LIMIT
        );
        mergeCandidates(
                mergedCandidates,
                loadPersonalCandidates(signalContext),
                CandidateSource.PERSONAL,
                PERSONAL_CANDIDATE_LIMIT
        );
        mergeCandidates(
                mergedCandidates,
                loadTrendCandidates(),
                CandidateSource.TREND,
                TREND_CANDIDATE_LIMIT
        );

        return mergedCandidates.values().stream()
                .map(accumulator -> new CandidatePlace(accumulator.place(), Set.copyOf(accumulator.sources())))
                .toList();
    }

    private List<MapPlace> loadGeoCandidates(double latitude, double longitude, double maxRadiusKm) {
        return loadNearbyCandidates(latitude, longitude, maxRadiusKm, GEO_CANDIDATE_LIMIT);
    }

    private List<MapPlace> loadPersonalCandidates(UserSignalContext signalContext) {
        if (signalContext.interactedPlaceIds().isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, MapPlace> personalCandidates = new LinkedHashMap<>();
        List<MapPlace> seedPlaces = mapPlaceRepository.findAllById(signalContext.interactedPlaceIds());

        for (MapPlace seedPlace : seedPlaces) {
            if (isEligibleCandidate(seedPlace)) {
                personalCandidates.putIfAbsent(seedPlace.getId(), seedPlace);
            }
        }

        List<MapPlace> expansionSeeds = seedPlaces.stream()
                .filter(this::isEligibleCandidate)
                .sorted(Comparator.comparingDouble(
                        (MapPlace place) -> signalContext.seedWeights().getOrDefault(place.getId(), 0.0d)
                ).reversed())
                .limit(PERSONAL_EXPANSION_SEED_LIMIT)
                .toList();

        for (MapPlace seedPlace : expansionSeeds) {
            for (MapPlace nearbyPlace : loadNearbyCandidates(
                    seedPlace.getLatitude(),
                    seedPlace.getLongitude(),
                    PERSONAL_EXPANSION_RADIUS_KM,
                    PERSONAL_EXPANSION_PER_SEED_LIMIT
            )) {
                personalCandidates.putIfAbsent(nearbyPlace.getId(), nearbyPlace);

                if (personalCandidates.size() >= PERSONAL_CANDIDATE_LIMIT) {
                    return List.copyOf(personalCandidates.values());
                }
            }
        }

        return List.copyOf(personalCandidates.values());
    }

    private List<MapPlace> loadTrendCandidates() {
        LocalDateTime trendUpdatedAfter = LocalDateTime.now(RECOMMENDATION_CLOCK).minusDays(TREND_LOOKBACK_DAYS);
        Page<PlaceRecommendationSnapshot> snapshotPage = placeRecommendationSnapshotRepository.findByUpdatedAtGreaterThanEqual(
                trendUpdatedAfter,
                PageRequest.of(
                        0,
                        TREND_CANDIDATE_LIMIT,
                        Sort.by(
                                Sort.Order.desc("updatedAt"),
                                Sort.Order.desc("latestPostCreatedAt"),
                                Sort.Order.desc("totalLikeCount"),
                                Sort.Order.desc("photoCount"),
                                Sort.Order.asc("placeId")
                        )
                )
        );

        List<Long> placeIds = snapshotPage.getContent().stream()
                .map(PlaceRecommendationSnapshot::getPlaceId)
                .toList();
        if (placeIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MapPlace> placeById = new HashMap<>();
        for (MapPlace place : mapPlaceRepository.findAllById(placeIds)) {
            if (isEligibleCandidate(place)) {
                placeById.put(place.getId(), place);
            }
        }

        List<MapPlace> orderedTrendCandidates = new ArrayList<>();
        for (Long placeId : placeIds) {
            MapPlace place = placeById.get(placeId);
            if (place != null) {
                orderedTrendCandidates.add(place);
            }
        }
        return orderedTrendCandidates;
    }

    private void mergeCandidates(
            Map<Long, CandidatePlaceAccumulator> mergedCandidates,
            List<MapPlace> candidates,
            CandidateSource source,
            int sourceLimit
    ) {
        int addedCount = 0;
        for (MapPlace candidate : candidates) {
            if (!isEligibleCandidate(candidate)) {
                continue;
            }

            CandidatePlaceAccumulator accumulator = mergedCandidates.get(candidate.getId());
            if (accumulator == null) {
                if (addedCount >= sourceLimit || mergedCandidates.size() >= CANDIDATE_POOL_LIMIT) {
                    continue;
                }
                mergedCandidates.put(candidate.getId(), new CandidatePlaceAccumulator(candidate, source));
                addedCount++;
                continue;
            }
            accumulator.addSource(source);
        }
    }

    private boolean isEligibleCandidate(MapPlace place) {
        return place != null
                && place.isOperating()
                && place.getLatitude() != null
                && place.getLongitude() != null;
    }

    private List<MapPlace> loadNearbyCandidates(
            double latitude,
            double longitude,
            double maxRadiusKm,
            int limit
    ) {
        double latitudeDelta = toLatitudeDelta(maxRadiusKm);
        double longitudeDelta = toLongitudeDelta(latitude, maxRadiusKm);

        double minLatitude = Math.max(-90d, latitude - latitudeDelta);
        double maxLatitude = Math.min(90d, latitude + latitudeDelta);
        double minLongitude = longitude - longitudeDelta;
        double maxLongitude = longitude + longitudeDelta;

        if (Double.isInfinite(longitudeDelta)) {
            return mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInLatitudeBand(
                    latitude,
                    longitude,
                    minLatitude,
                    maxLatitude,
                    PlaceOperatingStatus.OPERATING,
                    PageRequest.of(0, limit)
            );
        }

        if (minLongitude < -180d) {
            return mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInWrappedLongitudeBoundingBox(
                    latitude,
                    longitude,
                    minLatitude,
                    maxLatitude,
                    minLongitude + 360d,
                    maxLongitude,
                    PlaceOperatingStatus.OPERATING,
                    PageRequest.of(0, limit)
            );
        }

        if (maxLongitude > 180d) {
            return mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInWrappedLongitudeBoundingBox(
                    latitude,
                    longitude,
                    minLatitude,
                    maxLatitude,
                    minLongitude,
                    maxLongitude - 360d,
                    PlaceOperatingStatus.OPERATING,
                    PageRequest.of(0, limit)
            );
        }

        return mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInBoundingBox(
                latitude,
                longitude,
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                PlaceOperatingStatus.OPERATING,
                PageRequest.of(0, limit)
        );
    }

    private double toLatitudeDelta(double radiusKm) {
        return radiusKm / 111.32d;
    }

    private double toLongitudeDelta(double latitude, double radiusKm) {
        double cosine = Math.cos(Math.toRadians(latitude));
        if (Math.abs(cosine) < 1.0e-6d) {
            return Double.POSITIVE_INFINITY;
        }
        return radiusKm / (111.32d * cosine);
    }
}
