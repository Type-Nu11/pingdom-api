package com.typenull.pingdom.place.application.service.recommendation.similarity;


import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 20km 이내 장소 쌍의 유사도를 다시 계산하고 더 이상 대상이 아닌 저장 쌍을 제거.
 * 전체 재동기화는 별도 청크 실행자가 담당하며 이 서비스는 단건 보정을 담당.
 * 장소 하나의 보정은 쌍별 UPSERT를 사용하고 해당 장소의 오래된 이웃 쌍만 정리.
 */
@Service
@RequiredArgsConstructor
public class PlaceSimilaritySnapshotResyncService {

    private static final double MAX_SIMILARITY_RADIUS_KM = 20.0d;
    private static final double MAX_SIMILARITY_RADIUS_METERS = MAX_SIMILARITY_RADIUS_KM * 1_000d;
    private static final int RESYNC_BATCH_SIZE = 500;
    private static final Clock RESYNC_CLOCK = Clock.systemUTC();
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
    private final JdbcTemplate jdbcTemplate;

    /**
     * 대상 장소 주변 20km 이내 장소와의 유사도만 재계산하여 upsert하고 기존 쌍 중 범위에서 이탈한 상대를 삭제.
     * 반환값은 저장 예정 쌍 수와 삭제 건수. 대상 장소는 존재하는 유효 좌표 객체여야 하며 호출자가 필요한 장소 잠금을 확보.
     */
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

    public record SimilaritySnapshotResyncResult(long synchronizedSnapshotCount, long deletedSnapshotCount) {
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
