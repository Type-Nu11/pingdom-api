package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceSimilaritySnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceSimilaritySnapshotResyncService {

    private static final double MAX_SIMILARITY_RADIUS_KM = 20.0d;
    private static final int CANDIDATE_POOL_LIMIT = 300;
    private static final int RESYNC_BATCH_SIZE = 500;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Transactional
    public SimilaritySnapshotResyncResult resyncAll() {
        List<MapPlace> places = mapPlaceRepository.findAllWithCoordinates(Pageable.unpaged());
        if (places.isEmpty()) {
            long deletedSnapshotCount = placeSimilaritySnapshotRepository.count();
            if (deletedSnapshotCount > 0L) {
                placeSimilaritySnapshotRepository.deleteAllInBatch();
            }
            return new SimilaritySnapshotResyncResult(0L, deletedSnapshotCount);
        }

        Map<Long, MapPlace> placeIndex = new HashMap<>();
        for (MapPlace place : places) {
            placeIndex.put(place.getId(), place);
        }

        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                placeRecommendationSimilarityService.buildContext(placeIndex.keySet(), placeIndex, false);
        Map<PlaceRecommendationSimilarityService.PlacePairKey, PlaceSimilaritySnapshot> existingSnapshotByPair =
                new HashMap<>();
        for (PlaceSimilaritySnapshot snapshot : placeSimilaritySnapshotRepository.findByPlaceIdsWithin(placeIndex.keySet())) {
            existingSnapshotByPair.put(
                    PlaceRecommendationSimilarityService.PlacePairKey.of(
                            snapshot.getLeftPlaceId(),
                            snapshot.getRightPlaceId()
                    ),
                    snapshot
            );
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        Set<PlaceRecommendationSimilarityService.PlacePairKey> synchronizedPairKeys = new HashSet<>();
        List<PlaceSimilaritySnapshot> snapshotsToSave = new ArrayList<>();

        for (MapPlace basePlace : places) {
            for (MapPlace nearbyPlace : loadNearbyPlaces(basePlace)) {
                if (nearbyPlace.getId() <= basePlace.getId()) {
                    continue;
                }

                PlaceRecommendationSimilarityService.PlacePairKey pairKey =
                        PlaceRecommendationSimilarityService.PlacePairKey.of(basePlace.getId(), nearbyPlace.getId());
                PlaceRecommendationSimilarityService.SimilarityScore similarityScore =
                        placeRecommendationSimilarityService.score(basePlace.getId(), nearbyPlace.getId(), similarityContext);
                PlaceSimilaritySnapshot snapshot = existingSnapshotByPair.get(pairKey);
                if (snapshot == null) {
                    snapshot = PlaceSimilaritySnapshot.builder()
                            .leftPlaceId(pairKey.leftPlaceId())
                            .rightPlaceId(pairKey.rightPlaceId())
                            .updatedAt(syncedAt)
                            .build();
                }

                snapshot.synchronize(
                        similarityScore.geoKernel(),
                        similarityScore.coBookmarkPmi(),
                        similarityScore.coLikeCosine(),
                        similarityScore.trendSimilarity(),
                        similarityScore.totalSimilarity(),
                        syncedAt
                );
                snapshotsToSave.add(snapshot);
                synchronizedPairKeys.add(pairKey);
            }
        }

        if (!snapshotsToSave.isEmpty()) {
            placeSimilaritySnapshotRepository.saveAll(snapshotsToSave);
        }

        long deletedSnapshotCount = deleteOrphanSnapshots(synchronizedPairKeys);
        return new SimilaritySnapshotResyncResult(synchronizedPairKeys.size(), deletedSnapshotCount);
    }

    private long deleteOrphanSnapshots(Set<PlaceRecommendationSimilarityService.PlacePairKey> synchronizedPairKeys) {
        int pageNumber = 0;
        List<Long> orphanSnapshotIds = new ArrayList<>();

        while (true) {
            Page<PlaceSimilaritySnapshot> snapshotPage = placeSimilaritySnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            orphanSnapshotIds.addAll(snapshotPage.getContent().stream()
                    .filter(snapshot -> !synchronizedPairKeys.contains(
                            PlaceRecommendationSimilarityService.PlacePairKey.of(
                                    snapshot.getLeftPlaceId(),
                                    snapshot.getRightPlaceId()
                            )
                    ))
                    .map(PlaceSimilaritySnapshot::getId)
                    .toList());

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        if (!orphanSnapshotIds.isEmpty()) {
            placeSimilaritySnapshotRepository.deleteAllByIdInBatch(orphanSnapshotIds);
        }
        return orphanSnapshotIds.size();
    }

    private List<MapPlace> loadNearbyPlaces(MapPlace basePlace) {
        double latitudeDelta = toLatitudeDelta(MAX_SIMILARITY_RADIUS_KM);
        double longitudeDelta = toLongitudeDelta(basePlace.getLatitude(), MAX_SIMILARITY_RADIUS_KM);

        double minLatitude = Math.max(-90d, basePlace.getLatitude() - latitudeDelta);
        double maxLatitude = Math.min(90d, basePlace.getLatitude() + latitudeDelta);
        Pageable pageable = PageRequest.of(0, CANDIDATE_POOL_LIMIT);

        if (Double.isInfinite(longitudeDelta)) {
            return mapPlaceRepository.findRecommendationCandidatesInLatitudeBand(
                    basePlace.getLatitude(),
                    basePlace.getLongitude(),
                    minLatitude,
                    maxLatitude,
                    pageable
            ).stream()
                    .filter(candidate -> isWithinSimilarityRadius(basePlace, candidate))
                    .toList();
        }

        double minLongitude = basePlace.getLongitude() - longitudeDelta;
        double maxLongitude = basePlace.getLongitude() + longitudeDelta;

        if (minLongitude < -180d) {
            return mapPlaceRepository.findRecommendationCandidatesInWrappedLongitudeBoundingBox(
                    basePlace.getLatitude(),
                    basePlace.getLongitude(),
                    minLatitude,
                    maxLatitude,
                    minLongitude + 360d,
                    maxLongitude,
                    pageable
            ).stream()
                    .filter(candidate -> isWithinSimilarityRadius(basePlace, candidate))
                    .toList();
        }

        if (maxLongitude > 180d) {
            return mapPlaceRepository.findRecommendationCandidatesInWrappedLongitudeBoundingBox(
                    basePlace.getLatitude(),
                    basePlace.getLongitude(),
                    minLatitude,
                    maxLatitude,
                    minLongitude,
                    maxLongitude - 360d,
                    pageable
            ).stream()
                    .filter(candidate -> isWithinSimilarityRadius(basePlace, candidate))
                    .toList();
        }

        return mapPlaceRepository.findRecommendationCandidatesInBoundingBox(
                basePlace.getLatitude(),
                basePlace.getLongitude(),
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                pageable
        ).stream()
                .filter(candidate -> isWithinSimilarityRadius(basePlace, candidate))
                .toList();
    }

    private double toLatitudeDelta(double radiusKm) {
        return radiusKm / 111.32d;
    }

    private double toLongitudeDelta(double latitude, double radiusKm) {
        double cosine = Math.cos(Math.toRadians(latitude));
        if (Math.abs(cosine) < 1e-6) {
            return Double.POSITIVE_INFINITY;
        }
        return radiusKm / (111.32d * cosine);
    }

    private boolean isWithinSimilarityRadius(MapPlace basePlace, MapPlace candidatePlace) {
        double distanceMeters = calculateDistanceMeters(
                basePlace.getLatitude(),
                basePlace.getLongitude(),
                candidatePlace.getLatitude(),
                candidatePlace.getLongitude()
        );
        return distanceMeters <= MAX_SIMILARITY_RADIUS_KM * 1_000d;
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
        return 6_371_000d * c;
    }

    public record SimilaritySnapshotResyncResult(long synchronizedSnapshotCount, long deletedSnapshotCount) {
    }
}
