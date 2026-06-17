package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceSimilaritySnapshotRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
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
    private static final int PLACE_PAGE_SIZE = 500;
    private static final int RESYNC_BATCH_SIZE = 500;
    private static final Clock RESYNC_CLOCK = Clock.systemUTC();
    private static final String UPDATE_SNAPSHOT_QUERY = """
            UPDATE PlaceSimilaritySnapshot s
            SET s.geoKernelScore = :geoKernelScore,
                s.coBookmarkPmiScore = :coBookmarkPmiScore,
                s.coLikeCosineScore = :coLikeCosineScore,
                s.trendSimilarityScore = :trendSimilarityScore,
                s.totalSimilarityScore = :totalSimilarityScore,
                s.updatedAt = :updatedAt
            WHERE s.id = :id
            """;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;
    private final EntityManager entityManager;

    @Transactional
    public SimilaritySnapshotResyncResult resyncAll() {
        Set<Long> activePlaceIds = collectActivePlaceIds();
        if (activePlaceIds.isEmpty()) {
            long deletedSnapshotCount = placeSimilaritySnapshotRepository.count();
            if (deletedSnapshotCount > 0L) {
                placeSimilaritySnapshotRepository.deleteAllInBatch();
            }
            return new SimilaritySnapshotResyncResult(0L, deletedSnapshotCount);
        }

        long totalBookmarkUserCount = placeRecommendationSimilarityService.cachedTotalBookmarkUserCount();
        ExistingSnapshotState existingSnapshotState = loadExistingSnapshotState(activePlaceIds);
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                placeRecommendationSimilarityService.buildContext(
                        activePlaceIds,
                        Map.of(),
                        false,
                        totalBookmarkUserCount
                );
        LocalDateTime syncedAt = LocalDateTime.ofInstant(RESYNC_CLOCK.instant(), ZoneOffset.UTC);
        entityManager.clear();

        long synchronizedSnapshotCount = synchronizeSnapshots(
                similarityContext,
                existingSnapshotState.existingSnapshotByPair(),
                syncedAt
        );

        existingSnapshotState.orphanSnapshotIds().addAll(existingSnapshotState.existingSnapshotByPair().values().stream()
                .map(ExistingSnapshotRef::id)
                .toList());

        long deletedSnapshotCount = deleteSnapshotIds(existingSnapshotState.orphanSnapshotIds());
        return new SimilaritySnapshotResyncResult(synchronizedSnapshotCount, deletedSnapshotCount);
    }

    private Set<Long> collectActivePlaceIds() {
        Set<Long> activePlaceIds = new HashSet<>();
        int pageNumber = 0;

        while (true) {
            Page<MapPlace> placePage = mapPlaceRepository.findCoordinatePage(PageRequest.of(pageNumber, PLACE_PAGE_SIZE));
            if (placePage.isEmpty()) {
                break;
            }

            placePage.getContent().stream()
                    .map(MapPlace::getId)
                    .forEach(activePlaceIds::add);

            if (!placePage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return activePlaceIds;
    }

    private ExistingSnapshotState loadExistingSnapshotState(Set<Long> activePlaceIds) {
        Map<PlaceRecommendationSimilarityService.PlacePairKey, ExistingSnapshotRef> existingSnapshotByPair =
                new HashMap<>();
        List<Long> orphanSnapshotIds = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            Page<PlaceSimilaritySnapshot> snapshotPage = placeSimilaritySnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            for (PlaceSimilaritySnapshot snapshot : snapshotPage.getContent()) {
                if (!activePlaceIds.contains(snapshot.getLeftPlaceId())
                        || !activePlaceIds.contains(snapshot.getRightPlaceId())) {
                    orphanSnapshotIds.add(snapshot.getId());
                    continue;
                }

                existingSnapshotByPair.put(
                        PlaceRecommendationSimilarityService.PlacePairKey.of(
                                snapshot.getLeftPlaceId(),
                                snapshot.getRightPlaceId()
                        ),
                        new ExistingSnapshotRef(snapshot.getId())
                );
            }

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return new ExistingSnapshotState(existingSnapshotByPair, orphanSnapshotIds);
    }

    private long synchronizeSnapshots(
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            Map<PlaceRecommendationSimilarityService.PlacePairKey, ExistingSnapshotRef> existingSnapshotByPair,
            LocalDateTime syncedAt
    ) {
        ArrayDeque<MapPlace> slidingWindow = new ArrayDeque<>();
        List<PlaceSimilaritySnapshot> snapshotInsertBatch = new ArrayList<>(RESYNC_BATCH_SIZE);
        List<ExistingSnapshotUpdate> snapshotUpdateBatch = new ArrayList<>(RESYNC_BATCH_SIZE);
        double latitudeDelta = toLatitudeDelta(MAX_SIMILARITY_RADIUS_KM);
        long synchronizedSnapshotCount = 0L;
        int pageNumber = 0;

        while (true) {
            Page<MapPlace> placePage = mapPlaceRepository.findCoordinatePage(PageRequest.of(pageNumber, PLACE_PAGE_SIZE));
            if (placePage.isEmpty()) {
                break;
            }

            for (MapPlace currentPlace : placePage.getContent()) {
                // 위도 오름차순 페이지를 유지하면서 20km 위도 범위를 벗어난 이전 장소는 창에서 제거한다.
                while (!slidingWindow.isEmpty()
                        && (currentPlace.getLatitude() - slidingWindow.peekFirst().getLatitude()) > latitudeDelta) {
                    slidingWindow.removeFirst();
                }

                for (MapPlace previousPlace : slidingWindow) {
                    if (!isWithinSimilarityRadius(previousPlace, currentPlace)) {
                        continue;
                    }

                    PlaceRecommendationSimilarityService.PlacePairKey pairKey =
                            PlaceRecommendationSimilarityService.PlacePairKey.of(previousPlace.getId(), currentPlace.getId());
                    PlaceRecommendationSimilarityService.SimilarityScore similarityScore =
                            placeRecommendationSimilarityService.score(previousPlace, currentPlace, similarityContext);
                    ExistingSnapshotRef existingSnapshot = existingSnapshotByPair.remove(pairKey);
                    if (existingSnapshot == null) {
                        PlaceSimilaritySnapshot snapshot = PlaceSimilaritySnapshot.builder()
                                .leftPlaceId(pairKey.leftPlaceId())
                                .rightPlaceId(pairKey.rightPlaceId())
                                .updatedAt(syncedAt)
                                .build();
                        snapshot.synchronize(
                                similarityScore.geoKernel(),
                                similarityScore.coBookmarkPmi(),
                                similarityScore.coLikeCosine(),
                                similarityScore.trendSimilarity(),
                                similarityScore.totalSimilarity(),
                                syncedAt
                        );
                        snapshotInsertBatch.add(snapshot);
                    } else {
                        snapshotUpdateBatch.add(new ExistingSnapshotUpdate(
                                existingSnapshot.id(),
                                similarityScore.geoKernel(),
                                similarityScore.coBookmarkPmi(),
                                similarityScore.coLikeCosine(),
                                similarityScore.trendSimilarity(),
                                similarityScore.totalSimilarity(),
                                syncedAt
                        ));
                    }
                    synchronizedSnapshotCount++;

                    if (snapshotInsertBatch.size() + snapshotUpdateBatch.size() >= RESYNC_BATCH_SIZE) {
                        persistSnapshotBatch(snapshotInsertBatch, snapshotUpdateBatch);
                    }
                }

                slidingWindow.addLast(currentPlace);
            }

            if (!placePage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        if (!snapshotInsertBatch.isEmpty() || !snapshotUpdateBatch.isEmpty()) {
            persistSnapshotBatch(snapshotInsertBatch, snapshotUpdateBatch);
        }

        return synchronizedSnapshotCount;
    }

    private void persistSnapshotBatch(
            List<PlaceSimilaritySnapshot> snapshotInsertBatch,
            List<ExistingSnapshotUpdate> snapshotUpdateBatch
    ) {
        if (!snapshotInsertBatch.isEmpty()) {
            placeSimilaritySnapshotRepository.saveAll(snapshotInsertBatch);
        }
        entityManager.flush();
        for (ExistingSnapshotUpdate snapshotUpdate : snapshotUpdateBatch) {
            entityManager.createQuery(UPDATE_SNAPSHOT_QUERY)
                    .setParameter("geoKernelScore", snapshotUpdate.geoKernelScore())
                    .setParameter("coBookmarkPmiScore", snapshotUpdate.coBookmarkPmiScore())
                    .setParameter("coLikeCosineScore", snapshotUpdate.coLikeCosineScore())
                    .setParameter("trendSimilarityScore", snapshotUpdate.trendSimilarityScore())
                    .setParameter("totalSimilarityScore", snapshotUpdate.totalSimilarityScore())
                    .setParameter("updatedAt", snapshotUpdate.updatedAt())
                    .setParameter("id", snapshotUpdate.id())
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
        snapshotInsertBatch.clear();
        snapshotUpdateBatch.clear();
    }

    private long deleteSnapshotIds(List<Long> snapshotIds) {
        if (snapshotIds.isEmpty()) {
            return 0L;
        }

        for (int start = 0; start < snapshotIds.size(); start += RESYNC_BATCH_SIZE) {
            int end = Math.min(start + RESYNC_BATCH_SIZE, snapshotIds.size());
            placeSimilaritySnapshotRepository.deleteAllByIdInBatch(snapshotIds.subList(start, end));
        }
        return snapshotIds.size();
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

    private record ExistingSnapshotState(
            Map<PlaceRecommendationSimilarityService.PlacePairKey, ExistingSnapshotRef> existingSnapshotByPair,
            List<Long> orphanSnapshotIds
    ) {
    }

    private record ExistingSnapshotRef(Long id) {
    }

    private record ExistingSnapshotUpdate(
            Long id,
            double geoKernelScore,
            double coBookmarkPmiScore,
            double coLikeCosineScore,
            double trendSimilarityScore,
            double totalSimilarityScore,
            LocalDateTime updatedAt
    ) {
    }
}
