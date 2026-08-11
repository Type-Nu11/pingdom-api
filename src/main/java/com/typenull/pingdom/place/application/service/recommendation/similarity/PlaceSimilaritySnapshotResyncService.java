package com.typenull.pingdom.place.application.service.recommendation.similarity;


import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
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
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceSimilaritySnapshotResyncService {

    private static final double MAX_SIMILARITY_RADIUS_KM = 20.0d;
    private static final double MAX_SIMILARITY_RADIUS_METERS = MAX_SIMILARITY_RADIUS_KM * 1_000d;
    private static final int PLACE_PAGE_SIZE = 500;
    private static final int RESYNC_BATCH_SIZE = 500;
    private static final Clock RESYNC_CLOCK = Clock.systemUTC();
    private static final String UPDATE_SNAPSHOT_SQL = """
            UPDATE place_similarity_snapshot
            SET geo_kernel_score = ?,
                co_bookmark_pmi_score = ?,
                co_like_cosine_score = ?,
                trend_similarity_score = ?,
                total_similarity_score = ?,
                updated_at = ?
            WHERE place_similarity_snapshot_id = ?
            """;
    private static final String UPSERT_SNAPSHOT_SQL = """
            INSERT INTO place_similarity_snapshot (
                left_place_id,
                right_place_id,
                geo_kernel_score,
                co_bookmark_pmi_score,
                co_like_cosine_score,
                trend_similarity_score,
                total_similarity_score,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (left_place_id, right_place_id)
            DO UPDATE SET
                geo_kernel_score = EXCLUDED.geo_kernel_score,
                co_bookmark_pmi_score = EXCLUDED.co_bookmark_pmi_score,
                co_like_cosine_score = EXCLUDED.co_like_cosine_score,
                trend_similarity_score = EXCLUDED.trend_similarity_score,
                total_similarity_score = EXCLUDED.total_similarity_score,
                updated_at = EXCLUDED.updated_at
            """;

    private final MapPlaceCoordinateQueryRepository mapPlaceCoordinateQueryRepository;
    private final PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;
    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

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

    @Transactional
    public SimilaritySnapshotResyncResult resyncPlace(MapPlace targetPlace) {
        List<MapPlace> nearbyPlaces = mapPlaceCoordinateQueryRepository.findNearbyPlaces(
                targetPlace.getId(),
                MAX_SIMILARITY_RADIUS_METERS
        );
        Set<Long> nearbyPlaceIds = new HashSet<>();
        Map<Long, MapPlace> placeIndex = new HashMap<>();
        placeIndex.put(targetPlace.getId(), targetPlace);
        for (MapPlace nearbyPlace : nearbyPlaces) {
            nearbyPlaceIds.add(nearbyPlace.getId());
            placeIndex.put(nearbyPlace.getId(), nearbyPlace);
        }

        List<PlaceSimilaritySnapshot> existingSnapshots =
                placeSimilaritySnapshotRepository.findByPlaceId(targetPlace.getId());
        List<Long> staleSnapshotIds = existingSnapshots.stream()
                .filter(snapshot -> !nearbyPlaceIds.contains(otherPlaceId(snapshot, targetPlace.getId())))
                .map(PlaceSimilaritySnapshot::getId)
                .toList();

        LocalDateTime syncedAt = LocalDateTime.ofInstant(RESYNC_CLOCK.instant(), ZoneOffset.UTC);
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                placeRecommendationSimilarityService.buildContext(
                        placeIndex.keySet(),
                        placeIndex,
                        false
                );
        List<ScopedSnapshotUpsert> snapshotUpserts = new ArrayList<>(nearbyPlaces.size());
        for (MapPlace nearbyPlace : nearbyPlaces) {
            PlaceRecommendationSimilarityService.PlacePairKey pairKey =
                    PlaceRecommendationSimilarityService.PlacePairKey.of(targetPlace.getId(), nearbyPlace.getId());
            PlaceRecommendationSimilarityService.SimilarityScore score =
                    placeRecommendationSimilarityService.score(targetPlace, nearbyPlace, similarityContext);
            snapshotUpserts.add(new ScopedSnapshotUpsert(
                    pairKey.leftPlaceId(),
                    pairKey.rightPlaceId(),
                    score.geoKernel(),
                    score.coBookmarkPmi(),
                    score.coLikeCosine(),
                    score.trendSimilarity(),
                    score.totalSimilarity(),
                    syncedAt
            ));
        }

        upsertScopedSnapshots(snapshotUpserts);
        long deletedSnapshotCount = deleteSnapshotIds(staleSnapshotIds);
        return new SimilaritySnapshotResyncResult(snapshotUpserts.size(), deletedSnapshotCount);
    }

    @Transactional
    public long deleteForPlace(Long placeId) {
        List<Long> snapshotIds = placeSimilaritySnapshotRepository.findByPlaceId(placeId).stream()
                .map(PlaceSimilaritySnapshot::getId)
                .toList();
        return deleteSnapshotIds(snapshotIds);
    }

    private Set<Long> collectActivePlaceIds() {
        Set<Long> activePlaceIds = new HashSet<>();
        int pageNumber = 0;

        while (true) {
            Page<MapPlace> placePage = mapPlaceCoordinateQueryRepository.findCoordinatePage(PageRequest.of(pageNumber, PLACE_PAGE_SIZE));
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
        Long lastSeenSnapshotId = 0L;

        while (true) {
            Slice<PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection> snapshotPage =
                    placeSimilaritySnapshotRepository.findExistingSnapshotSlice(
                            lastSeenSnapshotId,
                            PageRequest.of(0, RESYNC_BATCH_SIZE)
                    );
            if (snapshotPage.isEmpty()) {
                break;
            }

            for (PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection snapshot : snapshotPage.getContent()) {
                lastSeenSnapshotId = snapshot.getId();
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
            Page<MapPlace> placePage = mapPlaceCoordinateQueryRepository.findCoordinatePage(PageRequest.of(pageNumber, PLACE_PAGE_SIZE));
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
        if (!snapshotUpdateBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(UPDATE_SNAPSHOT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int index) throws java.sql.SQLException {
                    ExistingSnapshotUpdate snapshotUpdate = snapshotUpdateBatch.get(index);
                    ps.setDouble(1, snapshotUpdate.geoKernelScore());
                    ps.setDouble(2, snapshotUpdate.coBookmarkPmiScore());
                    ps.setDouble(3, snapshotUpdate.coLikeCosineScore());
                    ps.setDouble(4, snapshotUpdate.trendSimilarityScore());
                    ps.setDouble(5, snapshotUpdate.totalSimilarityScore());
                    ps.setObject(6, snapshotUpdate.updatedAt());
                    ps.setLong(7, snapshotUpdate.id());
                }

                @Override
                public int getBatchSize() {
                    return snapshotUpdateBatch.size();
                }
            });
        }
        entityManager.flush();
        entityManager.clear();
        snapshotInsertBatch.clear();
        snapshotUpdateBatch.clear();
    }

    private void upsertScopedSnapshots(List<ScopedSnapshotUpsert> snapshotUpserts) {
        if (snapshotUpserts.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SNAPSHOT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int index) throws java.sql.SQLException {
                ScopedSnapshotUpsert upsert = snapshotUpserts.get(index);
                ps.setLong(1, upsert.leftPlaceId());
                ps.setLong(2, upsert.rightPlaceId());
                ps.setDouble(3, upsert.geoKernelScore());
                ps.setDouble(4, upsert.coBookmarkPmiScore());
                ps.setDouble(5, upsert.coLikeCosineScore());
                ps.setDouble(6, upsert.trendSimilarityScore());
                ps.setDouble(7, upsert.totalSimilarityScore());
                ps.setObject(8, upsert.updatedAt());
            }

            @Override
            public int getBatchSize() {
                return snapshotUpserts.size();
            }
        });
    }

    private Long otherPlaceId(PlaceSimilaritySnapshot snapshot, Long targetPlaceId) {
        return snapshot.getLeftPlaceId().equals(targetPlaceId)
                ? snapshot.getRightPlaceId()
                : snapshot.getLeftPlaceId();
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
        return distanceMeters <= MAX_SIMILARITY_RADIUS_METERS;
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

    private record ScopedSnapshotUpsert(
            Long leftPlaceId,
            Long rightPlaceId,
            double geoKernelScore,
            double coBookmarkPmiScore,
            double coLikeCosineScore,
            double trendSimilarityScore,
            double totalSimilarityScore,
            LocalDateTime updatedAt
    ) {
    }
}
