package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 반환 행과 입력 쌍을 청크 크기로 제한한다. 사용자 집합은 DB에서 집계하여 JVM으로 가져오지 않는다. */
@Repository
@RequiredArgsConstructor
public class RecommendationResyncQueryRepository {
    public static final int CHUNK_SIZE = 500;
    private final JdbcTemplate jdbcTemplate;

    // 기존 전체 재동기화의 위도 창과 반경/구면 거리 규칙을 함께 보존한다.
    private static final String HAVERSINE = """
            (power(sin(radians(r.latitude-l.latitude)/2),2)
             + cos(radians(l.latitude))*cos(radians(r.latitude))
               *power(sin(radians(r.longitude-l.longitude)/2),2))
            """;
    private static final String DISTANCE = "(6371000.0 * 2 * atan2(sqrt(LEAST(1.0, " + HAVERSINE
            + ")), sqrt(GREATEST(0.0, 1.0 - " + HAVERSINE + "))))";
    private static final String VALID_PAIR = """
            l.latitude IS NOT NULL AND l.longitude IS NOT NULL
            AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
            AND abs(l.latitude-r.latitude) <= (20.0/111.32)
            AND
            """ + DISTANCE + " <= 20000.0";

    public List<Long> placeIds(long after, long upper) {
        return jdbcTemplate.queryForList("""
                SELECT map_place_id FROM map_place WHERE map_place_id > ? AND map_place_id <= ?
                ORDER BY map_place_id LIMIT 500
                """, Long.class, after, upper);
    }

    public List<Pair> pairs(long left, long right, long upper) {
        return jdbcTemplate.query("""
                SELECT l.map_place_id, r.map_place_id FROM map_place l JOIN map_place r
                  ON l.map_place_id < r.map_place_id
                WHERE (l.map_place_id > ? OR (l.map_place_id = ? AND r.map_place_id > ?))
                  AND l.map_place_id <= ? AND r.map_place_id <= ? AND
                """ + VALID_PAIR + " ORDER BY l.map_place_id,r.map_place_id LIMIT 500",
                (rs, row) -> new Pair(rs.getLong(1), rs.getLong(2)), left, left, right, upper, upper);
    }

    /** 장소 잠금 이후 현재 좌표와 반응을 읽어 이동/삭제된 후보를 재검증한다. */
    public List<Signals> signals(List<Pair> pairs) {
        if (pairs.isEmpty()) return List.of();
        String values = String.join(",", Collections.nCopies(pairs.size(), "(?::bigint,?::bigint)"));
        List<Object> args = new ArrayList<>(pairs.size() * 2);
        pairs.forEach(pair -> { args.add(pair.left()); args.add(pair.right()); });
        return jdbcTemplate.query("""
                WITH pairs(left_id,right_id) AS (VALUES
                """ + values + """
                )
                SELECT l.map_place_id AS left_id,r.map_place_id AS right_id,
                    bl.n AS bookmark_left, br.n AS bookmark_right, bi.n AS bookmark_intersection,
                    ll.n AS like_left, lr.n AS like_right, li.n AS like_intersection,
                    COALESCE(sl.photo_count,l.photo_count,0) AS photo_left,
                    COALESCE(sr.photo_count,r.photo_count,0) AS photo_right,
                    sl.latest_post_created_at AS latest_left,sr.latest_post_created_at AS latest_right
                FROM pairs p JOIN map_place l ON l.map_place_id=p.left_id
                  JOIN map_place r ON r.map_place_id=p.right_id
                  LEFT JOIN place_recommendation_snapshot sl ON sl.place_id=p.left_id
                  LEFT JOIN place_recommendation_snapshot sr ON sr.place_id=p.right_id
                  CROSS JOIN LATERAL (SELECT count(*) n FROM map_bookmark b WHERE b.place_id=p.left_id) bl
                  CROSS JOIN LATERAL (SELECT count(*) n FROM map_bookmark b WHERE b.place_id=p.right_id) br
                  CROSS JOIN LATERAL (SELECT count(*) n FROM map_bookmark a JOIN map_bookmark b
                    ON a.user_id=b.user_id WHERE a.place_id=p.left_id AND b.place_id=p.right_id) bi
                  CROSS JOIN LATERAL (SELECT count(*) n FROM
                    (SELECT DISTINCT k.user_id FROM map_image_like k JOIN map_image i ON i.map_image_id=k.map_image_id
                      WHERE i.map_place_id=p.left_id) q) ll
                  CROSS JOIN LATERAL (SELECT count(*) n FROM
                    (SELECT DISTINCT k.user_id FROM map_image_like k JOIN map_image i ON i.map_image_id=k.map_image_id
                      WHERE i.map_place_id=p.right_id) q) lr
                  CROSS JOIN LATERAL (SELECT count(*) n FROM
                    (SELECT k.user_id FROM map_image_like k JOIN map_image i ON i.map_image_id=k.map_image_id
                      WHERE i.map_place_id=p.left_id
                     INTERSECT
                     SELECT k.user_id FROM map_image_like k JOIN map_image i ON i.map_image_id=k.map_image_id
                      WHERE i.map_place_id=p.right_id) q) li
                WHERE
                """ + VALID_PAIR + " ORDER BY left_id,right_id", (rs, row) ->
                new Signals(rs.getLong("left_id"), rs.getLong("right_id"),
                        rs.getLong("bookmark_left"), rs.getLong("bookmark_right"), rs.getLong("bookmark_intersection"),
                        rs.getLong("like_left"), rs.getLong("like_right"), rs.getLong("like_intersection"),
                        rs.getLong("photo_left"), rs.getLong("photo_right"),
                        timestamp(rs.getTimestamp("latest_left")), timestamp(rs.getTimestamp("latest_right"))),
                args.toArray());
    }

    public void upsert(List<Score> scores) {
        if (scores.isEmpty()) return;
        jdbcTemplate.batchUpdate("""
                INSERT INTO place_similarity_snapshot(left_place_id,right_place_id,geo_kernel_score,
                    co_bookmark_pmi_score,co_like_cosine_score,trend_similarity_score,total_similarity_score,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(left_place_id,right_place_id) DO UPDATE SET
                    geo_kernel_score=EXCLUDED.geo_kernel_score,co_bookmark_pmi_score=EXCLUDED.co_bookmark_pmi_score,
                    co_like_cosine_score=EXCLUDED.co_like_cosine_score,trend_similarity_score=EXCLUDED.trend_similarity_score,
                    total_similarity_score=EXCLUDED.total_similarity_score,updated_at=EXCLUDED.updated_at
                """, scores, CHUNK_SIZE, (ps, score) -> {
            ps.setLong(1, score.left()); ps.setLong(2, score.right());
            ps.setDouble(3, score.geo()); ps.setDouble(4, score.bookmark());
            ps.setDouble(5, score.like()); ps.setDouble(6, score.trend());
            ps.setDouble(7, score.total()); ps.setObject(8, score.updatedAt());
        });
    }

    public List<Long> snapshotOrphans(long after, long upper) {
        return jdbcTemplate.queryForList("""
                SELECT s.place_id FROM place_recommendation_snapshot s
                WHERE s.place_id > ? AND s.place_id <= ?
                  AND NOT EXISTS(SELECT 1 FROM map_place p WHERE p.map_place_id=s.place_id)
                ORDER BY s.place_id LIMIT 500
                """, Long.class, after, upper);
    }

    public List<Long> versionOrphans(long after, long upper) {
        return jdbcTemplate.queryForList("""
                SELECT s.place_recommendation_version_snapshot_id FROM place_recommendation_version_snapshot s
                WHERE s.place_recommendation_version_snapshot_id > ? AND s.place_recommendation_version_snapshot_id <= ?
                  AND NOT EXISTS(SELECT 1 FROM map_place p WHERE p.map_place_id=s.place_id)
                ORDER BY s.place_recommendation_version_snapshot_id LIMIT 500
                """, Long.class, after, upper);
    }

    public int deleteSnapshotOrphans(List<Long> ids) {
        if (ids.isEmpty()) return 0;
        return jdbcTemplate.update("DELETE FROM place_recommendation_snapshot s WHERE s.place_id IN (" + placeholders(ids)
                + ") AND NOT EXISTS(SELECT 1 FROM map_place p WHERE p.map_place_id=s.place_id)", ids.toArray());
    }

    public int deleteVersionOrphans(List<Long> ids) {
        if (ids.isEmpty()) return 0;
        return jdbcTemplate.update("DELETE FROM place_recommendation_version_snapshot s WHERE s.place_recommendation_version_snapshot_id IN ("
                + placeholders(ids) + ") AND NOT EXISTS(SELECT 1 FROM map_place p WHERE p.map_place_id=s.place_id)", ids.toArray());
    }

    public List<ExistingPair> existingPairs(long after, long upper) {
        return jdbcTemplate.query("""
                SELECT place_similarity_snapshot_id,left_place_id,right_place_id FROM place_similarity_snapshot
                WHERE place_similarity_snapshot_id > ? AND place_similarity_snapshot_id <= ?
                ORDER BY place_similarity_snapshot_id LIMIT 500
                """, (rs, row) -> new ExistingPair(rs.getLong(1), rs.getLong(2), rs.getLong(3)), after, upper);
    }

    public int deleteInvalidPairs(List<Long> ids) {
        if (ids.isEmpty()) return 0;
        return jdbcTemplate.update("DELETE FROM place_similarity_snapshot s WHERE s.place_similarity_snapshot_id IN ("
                + placeholders(ids) + """
                ) AND NOT EXISTS(SELECT 1 FROM map_place l JOIN map_place r
                  ON l.map_place_id=s.left_place_id AND r.map_place_id=s.right_place_id WHERE
                """ + VALID_PAIR + ")", ids.toArray());
    }

    private String placeholders(List<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record Pair(long left, long right) {}
    public record ExistingPair(long id, long left, long right) {}
    public record Signals(long left, long right, long bookmarkLeft, long bookmarkRight, long bookmarkIntersection,
                          long likeLeft, long likeRight, long likeIntersection, long photoLeft, long photoRight,
                          LocalDateTime latestLeft, LocalDateTime latestRight) {}
    public record Score(long left, long right, double geo, double bookmark, double like, double trend,
                        double total, LocalDateTime updatedAt) {}
}
