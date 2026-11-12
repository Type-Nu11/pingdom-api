package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 청크 실행자는 상태 행을 먼저 잠가 snapshot 변경과 커서 전진을 같은 트랜잭션으로 확정한다. */
@Repository
@RequiredArgsConstructor
public class RecommendationResyncCheckpointRepository {
    private final JdbcTemplate jdbcTemplate;

    public void claim(UUID token, long totalBookmarkUsers) {
        Boolean available = jdbcTemplate.queryForObject("""
                SELECT owner_token IS NULL OR lease_until <= clock_timestamp()
                FROM recommendation_resync_checkpoint WHERE id = 1 FOR UPDATE
                """, Boolean.class);
        if (!Boolean.TRUE.equals(available)) {
            throw new MapException(MapErrorCode.RECOMMENDATION_RESYNC_IN_PROGRESS);
        }
        // DONE이면 새 실행이며, 실패/프로세스 종료이면 마지막으로 커밋한 청크부터 이어 간다.
        jdbcTemplate.update("""
                UPDATE recommendation_resync_checkpoint SET
                    upper_place_id = CASE WHEN phase = 'DONE' THEN (SELECT COALESCE(MAX(map_place_id),0) FROM map_place) ELSE upper_place_id END,
                    upper_snapshot_id = CASE WHEN phase = 'DONE' THEN (SELECT COALESCE(MAX(place_id),0) FROM place_recommendation_snapshot) ELSE upper_snapshot_id END,
                    upper_version_id = CASE WHEN phase = 'DONE' THEN (SELECT COALESCE(MAX(place_recommendation_version_snapshot_id),0) FROM place_recommendation_version_snapshot) ELSE upper_version_id END,
                    upper_similarity_id = CASE WHEN phase = 'DONE' THEN (SELECT COALESCE(MAX(place_similarity_snapshot_id),0) FROM place_similarity_snapshot) ELSE upper_similarity_id END,
                    total_bookmark_users = CASE WHEN phase = 'DONE' THEN ? ELSE total_bookmark_users END,
                    place_count = CASE WHEN phase = 'DONE' THEN (SELECT COUNT(*) FROM map_place) ELSE place_count END,
                    cursor_left = CASE WHEN phase = 'DONE' THEN 0 ELSE cursor_left END,
                    cursor_right = CASE WHEN phase = 'DONE' THEN 0 ELSE cursor_right END,
                    synchronized_count = CASE WHEN phase = 'DONE' THEN 0 ELSE synchronized_count END,
                    deleted_count = CASE WHEN phase = 'DONE' THEN 0 ELSE deleted_count END,
                    synchronized_similarity_count = CASE WHEN phase = 'DONE' THEN 0 ELSE synchronized_similarity_count END,
                    deleted_similarity_count = CASE WHEN phase = 'DONE' THEN 0 ELSE deleted_similarity_count END,
                    synchronized_version_count = CASE WHEN phase = 'DONE' THEN 0 ELSE synchronized_version_count END,
                    deleted_version_count = CASE WHEN phase = 'DONE' THEN 0 ELSE deleted_version_count END,
                    phase = CASE WHEN phase = 'DONE' THEN 'COUNTERS' ELSE phase END,
                    owner_token = ?, lease_until = clock_timestamp() + interval '2 minutes'
                WHERE id = 1
                """, totalBookmarkUsers, token);
    }

    public Checkpoint lock(UUID token) {
        return jdbcTemplate.query("""
                SELECT *, owner_token = ? AND lease_until > clock_timestamp() AS owned
                FROM recommendation_resync_checkpoint WHERE id = 1 FOR UPDATE
                """, (rs, row) -> {
            if (!rs.getBoolean("owned")) {
                throw new MapException(MapErrorCode.RECOMMENDATION_RESYNC_IN_PROGRESS);
            }
            return new Checkpoint(Phase.valueOf(rs.getString("phase")), rs.getLong("cursor_left"),
                    rs.getLong("cursor_right"), rs.getLong("upper_place_id"), rs.getLong("upper_snapshot_id"),
                    rs.getLong("upper_version_id"), rs.getLong("upper_similarity_id"), rs.getLong("total_bookmark_users"),
                    new Counts(rs.getLong("place_count"), rs.getLong("synchronized_count"),
                            rs.getLong("deleted_count"), rs.getLong("synchronized_similarity_count"),
                            rs.getLong("deleted_similarity_count"), rs.getLong("synchronized_version_count"),
                            rs.getLong("deleted_version_count")));
        }, token).getFirst();
    }

    public void save(UUID token, Checkpoint state) {
        Counts result = state.result();
        int changed = jdbcTemplate.update("""
                UPDATE recommendation_resync_checkpoint SET phase = ?, cursor_left = ?, cursor_right = ?,
                    synchronized_count = ?, deleted_count = ?, synchronized_similarity_count = ?,
                    deleted_similarity_count = ?, synchronized_version_count = ?, deleted_version_count = ?,
                    lease_until = clock_timestamp() + interval '2 minutes',
                    owner_token = CASE WHEN ? = 'DONE' THEN NULL ELSE owner_token END
                WHERE id = 1 AND owner_token = ?
                """, state.phase().name(), state.left(), state.right(), result.synchronizedSnapshotCount(),
                result.deletedSnapshotCount(), result.synchronizedSimilaritySnapshotCount(),
                result.deletedSimilaritySnapshotCount(), result.synchronizedVersionSnapshotCount(),
                result.deletedVersionSnapshotCount(), state.phase().name(), token);
        if (changed != 1) {
            throw new MapException(MapErrorCode.RECOMMENDATION_RESYNC_IN_PROGRESS);
        }
    }

    public void release(UUID token) {
        jdbcTemplate.update("""
                UPDATE recommendation_resync_checkpoint SET owner_token = NULL, lease_until = NULL
                WHERE id = 1 AND owner_token = ?
                """, token);
    }

    public enum Phase { COUNTERS, SIMILARITY, SNAPSHOT_ORPHANS, VERSION_ORPHANS, SIMILARITY_STALE, DONE }

    public record Counts(long placeCount, long synchronizedSnapshotCount, long deletedSnapshotCount,
                         long synchronizedSimilaritySnapshotCount, long deletedSimilaritySnapshotCount,
                         long synchronizedVersionSnapshotCount, long deletedVersionSnapshotCount) {
        public static Counts empty() { return new Counts(0, 0, 0, 0, 0, 0, 0); }
    }

    public record Checkpoint(Phase phase, long left, long right, long upperPlaceId, long upperSnapshotId,
                             long upperVersionId, long upperSimilarityId, long totalBookmarkUsers,
                             Counts result) {
        public Checkpoint advance(Phase next, long nextLeft, long nextRight, Counts delta) {
            return new Checkpoint(next, nextLeft, nextRight, upperPlaceId, upperSnapshotId, upperVersionId,
                    upperSimilarityId, totalBookmarkUsers, new Counts(result.placeCount(),
                    result.synchronizedSnapshotCount() + delta.synchronizedSnapshotCount(),
                    result.deletedSnapshotCount() + delta.deletedSnapshotCount(),
                    result.synchronizedSimilaritySnapshotCount() + delta.synchronizedSimilaritySnapshotCount(),
                    result.deletedSimilaritySnapshotCount() + delta.deletedSimilaritySnapshotCount(),
                    result.synchronizedVersionSnapshotCount() + delta.synchronizedVersionSnapshotCount(),
                    result.deletedVersionSnapshotCount() + delta.deletedVersionSnapshotCount()));
        }
    }
}
