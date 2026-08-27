package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 사용자·장소별 상태 전이를 먼저 접어 반복 토글이 전국 순위에 누적되지 않게 합니다. */
public interface PlaceTrendQueryRepository extends Repository<MapPlace, Long> {

    @Query(value = """
            WITH initial_stream_state AS (
                SELECT DISTINCT ON (event.user_id, event.origin_place_id)
                    event.user_id, event.origin_place_id, event.event_type
                FROM map_bookmark_trend_event event
                WHERE event.occurred_at < :periodStart
                ORDER BY event.user_id, event.origin_place_id, event.occurred_at DESC, event.id DESC
            ),
            final_stream_state AS (
                SELECT DISTINCT ON (event.user_id, event.origin_place_id)
                    event.user_id, event.place_id, event.origin_place_id, event.event_type
                FROM map_bookmark_trend_event event
                WHERE event.occurred_at <= :periodEnd
                ORDER BY event.user_id, event.origin_place_id, event.occurred_at DESC, event.id DESC
            ),
            user_place_state AS (
                SELECT final_state.user_id,
                       final_state.place_id,
                       BOOL_OR(COALESCE(initial_state.event_type IN ('BASELINE_ACTIVE', 'ADDED'), FALSE)) AS initially_bookmarked,
                       BOOL_OR(final_state.event_type IN ('BASELINE_ACTIVE', 'ADDED')) AS finally_bookmarked
                FROM final_stream_state final_state
                LEFT JOIN initial_stream_state initial_state
                  ON initial_state.user_id = final_state.user_id
                 AND initial_state.origin_place_id = final_state.origin_place_id
                GROUP BY final_state.user_id, final_state.place_id
            ),
            growth AS (
                SELECT place_id,
                       COUNT(*) FILTER (WHERE NOT initially_bookmarked AND finally_bookmarked) AS bookmark_adds,
                       COUNT(*) FILTER (WHERE initially_bookmarked AND NOT finally_bookmarked) AS bookmark_removes
                FROM user_place_state
                GROUP BY place_id
            )
            SELECT map_place.map_place_id AS placeId,
                   map_place.place_name AS placeName,
                   map_place.category AS category,
                   map_place.address AS address,
                   image.image_url AS imageUrl,
                   growth.bookmark_adds AS bookmarkAdds,
                   growth.bookmark_removes AS bookmarkRemoves,
                   (growth.bookmark_adds - growth.bookmark_removes) AS netBookmarkGrowth,
                   (SELECT COUNT(*) FROM map_bookmark bookmark WHERE bookmark.place_id = map_place.map_place_id) AS bookmarkCount,
                   EXISTS (
                       SELECT 1
                       FROM map_bookmark bookmark
                       WHERE bookmark.place_id = map_place.map_place_id
                         AND bookmark.user_id = :userId
                   ) AS bookmarked
            FROM growth
            JOIN map_place map_place ON map_place.map_place_id = growth.place_id
            LEFT JOIN LATERAL (
                SELECT map_image.image_url
                FROM map_image
                WHERE map_image.map_place_id = map_place.map_place_id
                  AND map_image.visibility_status = 'ACTIVE'
                ORDER BY map_image.created_time DESC NULLS LAST, map_image.map_image_id DESC
                LIMIT 1
            ) image ON TRUE
            WHERE map_place.discovery_status = 'VISIBLE'
              AND map_place.operating_status = 'OPERATING'
              AND (growth.bookmark_adds - growth.bookmark_removes) > 0
            ORDER BY netBookmarkGrowth DESC,
                     bookmarkAdds DESC,
                     bookmarkCount DESC,
                     placeId DESC
            """, nativeQuery = true)
    List<PlaceTrendProjection> findTrends(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query(value = """
            WITH initial_stream_state AS (
                SELECT DISTINCT ON (event.user_id, event.origin_place_id)
                    event.user_id, event.origin_place_id, event.event_type
                FROM map_bookmark_trend_event event
                WHERE event.occurred_at < :periodStart
                ORDER BY event.user_id, event.origin_place_id, event.occurred_at DESC, event.id DESC
            ),
            final_stream_state AS (
                SELECT DISTINCT ON (event.user_id, event.origin_place_id)
                    event.user_id, event.place_id, event.origin_place_id, event.event_type
                FROM map_bookmark_trend_event event
                WHERE event.occurred_at <= :periodEnd
                ORDER BY event.user_id, event.origin_place_id, event.occurred_at DESC, event.id DESC
            ),
            user_place_state AS (
                SELECT final_state.user_id,
                       final_state.place_id,
                       BOOL_OR(COALESCE(initial_state.event_type IN ('BASELINE_ACTIVE', 'ADDED'), FALSE)) AS initially_bookmarked,
                       BOOL_OR(final_state.event_type IN ('BASELINE_ACTIVE', 'ADDED')) AS finally_bookmarked
                FROM final_stream_state final_state
                LEFT JOIN initial_stream_state initial_state
                  ON initial_state.user_id = final_state.user_id
                 AND initial_state.origin_place_id = final_state.origin_place_id
                GROUP BY final_state.user_id, final_state.place_id
            ),
            growth AS (
                SELECT place_id,
                       COUNT(*) FILTER (WHERE NOT initially_bookmarked AND finally_bookmarked) AS bookmark_adds,
                       COUNT(*) FILTER (WHERE initially_bookmarked AND NOT finally_bookmarked) AS bookmark_removes
                FROM user_place_state
                GROUP BY place_id
            )
            SELECT COUNT(*)
            FROM growth
            JOIN map_place map_place ON map_place.map_place_id = growth.place_id
            WHERE map_place.discovery_status = 'VISIBLE'
              AND map_place.operating_status = 'OPERATING'
              AND (growth.bookmark_adds - growth.bookmark_removes) > 0
            """, nativeQuery = true)
    long countTrends(@Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd);

    interface PlaceTrendProjection {
        Long getPlaceId();
        String getPlaceName();
        String getCategory();
        String getImageUrl();
        String getAddress();
        long getBookmarkAdds();
        long getBookmarkRemoves();
        long getNetBookmarkGrowth();
        long getBookmarkCount();
        boolean getBookmarked();
    }
}
