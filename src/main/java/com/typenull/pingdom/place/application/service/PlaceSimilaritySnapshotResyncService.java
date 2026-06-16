package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceSimilaritySnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
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
    private static final int RESYNC_BATCH_SIZE = 500;
    private static final Clock RESYNC_CLOCK = Clock.systemUTC();

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

        long totalBookmarkUserCount = placeRecommendationSimilarityService.cachedTotalBookmarkUserCount();
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                placeRecommendationSimilarityService.buildContext(
                        placeIndex.keySet(),
                        placeIndex,
                        false,
                        totalBookmarkUserCount
                );
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

        LocalDateTime syncedAt = LocalDateTime.ofInstant(RESYNC_CLOCK.instant(), ZoneOffset.UTC);
        long synchronizedSnapshotCount = 0L;
        List<PlaceSimilaritySnapshot> snapshotBatch = new ArrayList<>(RESYNC_BATCH_SIZE);
        List<MapPlace> sortedPlaces = places.stream()
                .sorted(Comparator.comparingDouble(MapPlace::getLatitude))
                .toList();
        double latitudeDelta = toLatitudeDelta(MAX_SIMILARITY_RADIUS_KM);

        for (int baseIndex = 0; baseIndex < sortedPlaces.size(); baseIndex++) {
            MapPlace basePlace = sortedPlaces.get(baseIndex);
            for (int candidateIndex = baseIndex + 1; candidateIndex < sortedPlaces.size(); candidateIndex++) {
                MapPlace nearbyPlace = sortedPlaces.get(candidateIndex);
                if ((nearbyPlace.getLatitude() - basePlace.getLatitude()) > latitudeDelta) {
                    break;
                }
                if (!isWithinSimilarityRadius(basePlace, nearbyPlace)) {
                    continue;
                }

                PlaceRecommendationSimilarityService.PlacePairKey pairKey =
                        PlaceRecommendationSimilarityService.PlacePairKey.of(basePlace.getId(), nearbyPlace.getId());
                PlaceRecommendationSimilarityService.SimilarityScore similarityScore =
                        placeRecommendationSimilarityService.score(basePlace.getId(), nearbyPlace.getId(), similarityContext);
                PlaceSimilaritySnapshot snapshot = existingSnapshotByPair.remove(pairKey);
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
                snapshotBatch.add(snapshot);
                synchronizedSnapshotCount++;

                if (snapshotBatch.size() >= RESYNC_BATCH_SIZE) {
                    placeSimilaritySnapshotRepository.saveAll(snapshotBatch);
                    snapshotBatch.clear();
                }
            }
        }

        if (!snapshotBatch.isEmpty()) {
            placeSimilaritySnapshotRepository.saveAll(snapshotBatch);
        }

        long deletedSnapshotCount = deleteOrphanSnapshots(existingSnapshotByPair, placeIndex.keySet());
        return new SimilaritySnapshotResyncResult(synchronizedSnapshotCount, deletedSnapshotCount);
    }

    private long deleteOrphanSnapshots(
            Map<PlaceRecommendationSimilarityService.PlacePairKey, PlaceSimilaritySnapshot> orphanSnapshotsByPair,
            Set<Long> activePlaceIds
    ) {
        Set<Long> orphanSnapshotIds = new HashSet<>();
        orphanSnapshotsByPair.values().stream()
                .map(PlaceSimilaritySnapshot::getId)
                .forEach(orphanSnapshotIds::add);

        int pageNumber = 0;
        while (true) {
            Page<PlaceSimilaritySnapshot> snapshotPage = placeSimilaritySnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            snapshotPage.getContent().stream()
                    .filter(snapshot -> !activePlaceIds.contains(snapshot.getLeftPlaceId())
                            || !activePlaceIds.contains(snapshot.getRightPlaceId()))
                    .map(PlaceSimilaritySnapshot::getId)
                    .forEach(orphanSnapshotIds::add);

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        if (orphanSnapshotIds.isEmpty()) {
            return 0L;
        }

        List<Long> orphanSnapshotIdList = new ArrayList<>(orphanSnapshotIds);
        for (int start = 0; start < orphanSnapshotIdList.size(); start += RESYNC_BATCH_SIZE) {
            int end = Math.min(start + RESYNC_BATCH_SIZE, orphanSnapshotIdList.size());
            placeSimilaritySnapshotRepository.deleteAllByIdInBatch(orphanSnapshotIdList.subList(start, end));
        }
        return orphanSnapshotIdList.size();
    }

    private double toLatitudeDelta(double radiusKm) {
        return radiusKm / 111.32d;
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
