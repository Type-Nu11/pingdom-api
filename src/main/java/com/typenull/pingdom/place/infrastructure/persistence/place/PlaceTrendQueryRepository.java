package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사용자·장소별 상태 전이를 먼저 접어 반복 토글이 전국 순위에 누적되지 않게 함.
 * 기간 시작 직전과 종료 시각 이하의 마지막 상태를 원래 장소별로 복원한 뒤 현재 장소·회원 단위로 통합.
 * 같은 집계 결과로 건수와 페이지를 읽고, 빈 페이지에도 전체 건수 행을 반환.
 */
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
            ),
            eligible AS MATERIALIZED (
                SELECT map_place.map_place_id AS place_id,
                       map_place.place_name,
                       map_place.category,
                       map_place.address,
                       map_place.image_url,
                       growth.bookmark_adds,
                       growth.bookmark_removes,
                       (growth.bookmark_adds - growth.bookmark_removes) AS net_bookmark_growth,
                       (SELECT COUNT(*) FROM map_bookmark bookmark WHERE bookmark.place_id = map_place.map_place_id) AS bookmark_count
                FROM growth
                JOIN map_place map_place ON map_place.map_place_id = growth.place_id
                WHERE map_place.discovery_status = 'VISIBLE'
                  AND map_place.operating_status = 'OPERATING'
                  AND (growth.bookmark_adds - growth.bookmark_removes) > 0
            ),
            total AS (
                SELECT COUNT(*) AS total_elements
                FROM eligible
            ),
            paged AS MATERIALIZED (
                SELECT *
                FROM eligible
                ORDER BY net_bookmark_growth DESC,
                         bookmark_adds DESC,
                         bookmark_count DESC,
                         place_id DESC
                LIMIT :limit OFFSET :offset
            )
            SELECT total.total_elements AS totalElements,
                   paged.place_id AS placeId,
                   paged.place_name AS placeName,
                   paged.category AS category,
                   paged.address AS address,
                   COALESCE(paged.image_url, image.image_url) AS imageUrl,
                   COALESCE(paged.bookmark_adds, 0) AS bookmarkAdds,
                   COALESCE(paged.bookmark_removes, 0) AS bookmarkRemoves,
                   COALESCE(paged.net_bookmark_growth, 0) AS netBookmarkGrowth,
                   COALESCE(paged.bookmark_count, 0) AS bookmarkCount,
                   EXISTS (
                       SELECT 1
                       FROM map_bookmark bookmark
                       WHERE bookmark.place_id = paged.place_id
                         AND bookmark.user_id = :userId
                   ) AS bookmarked
            FROM total
            LEFT JOIN paged ON TRUE
            LEFT JOIN LATERAL (
                SELECT map_image.image_url
                FROM map_image
                WHERE map_image.map_place_id = paged.place_id
                  AND map_image.visibility_status = 'ACTIVE'
                ORDER BY map_image.created_time DESC NULLS LAST, map_image.map_image_id DESC
                LIMIT 1
            ) image ON TRUE
            ORDER BY paged.net_bookmark_growth DESC,
                     paged.bookmark_adds DESC,
                     paged.bookmark_count DESC,
                     paged.place_id DESC
            """, nativeQuery = true)
    List<PlaceTrendProjection> findTrendPage(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    interface PlaceTrendProjection {
        long getTotalElements();
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
