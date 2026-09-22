package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 장소 랭킹 화면에 필요한 집계 결과만 PostgreSQL에서 조회합니다. */
public interface PlaceRankingQueryRepository extends Repository<MapPlace, Long> {

    /**
     * 기간 시작 이후 작성된 활성 게시물의 현재 누적 좋아요를 합산해 양수인 장소만 셉니다.
     * 좋아요가 눌린 시각의 구간 집계가 아니며 장소의 운영·탐색 상태나 기간 종료 상한은 이 쿼리에 포함되지 않습니다.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT mp.map_place_id
                FROM map_image mi
                JOIN map_place mp ON mp.map_place_id = mi.map_place_id
                WHERE mi.visibility_status = 'ACTIVE'
                  AND mi.created_time IS NOT NULL
                  AND mi.created_time >= :periodStart
                  AND (CAST(:category AS text) IS NULL
                       OR (mp.category IS NOT NULL AND LOWER(mp.category) = :category))
                  AND (
                      :local = FALSE
                      OR (
                          mp.location IS NOT NULL
                          AND ST_DWithin(
                              mp.location::geography,
                              ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                              :radiusMeters
                          )
                      )
                  )
                GROUP BY mp.map_place_id
                HAVING COALESCE(SUM(mi.like_count), 0) > 0
            ) ranked_places
            """, nativeQuery = true)
    long countRankedPlaces(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("category") String category,
            @Param("local") boolean local,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") double radiusMeters
    );

    @Query(value = """
            WITH ranked_places AS (
                SELECT mp.map_place_id AS placeId,
                       mp.place_name AS placeName,
                       mp.category AS category,
                       mp.latitude AS latitude,
                       mp.longitude AS longitude,
                       mp.registrant AS registrantUsername,
                       CASE
                           WHEN :local = TRUE THEN ST_Distance(
                               mp.location::geography,
                               ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                           )
                           ELSE NULL
                       END AS distanceMeters,
                       COALESCE(SUM(mi.like_count), 0) AS likeCount,
                       COUNT(mi.map_image_id) AS postCount
                FROM map_image mi
                JOIN map_place mp ON mp.map_place_id = mi.map_place_id
                WHERE mi.visibility_status = 'ACTIVE'
                  AND mi.created_time IS NOT NULL
                  AND mi.created_time >= :periodStart
                  AND (CAST(:category AS text) IS NULL
                       OR (mp.category IS NOT NULL AND LOWER(mp.category) = :category))
                  AND (
                      :local = FALSE
                      OR (
                          mp.location IS NOT NULL
                          AND ST_DWithin(
                              mp.location::geography,
                              ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                              :radiusMeters
                          )
                      )
                  )
                GROUP BY mp.map_place_id,
                         mp.place_name,
                         mp.category,
                         mp.latitude,
                         mp.longitude,
                         mp.registrant,
                         mp.location
                HAVING COALESCE(SUM(mi.like_count), 0) > 0
            )
            SELECT ranked.placeId,
                   ranked.placeName,
                   ranked.category,
                   ranked.latitude,
                   ranked.longitude,
                   ranked.registrantUsername,
                   ranked.distanceMeters,
                   ranked.likeCount,
                   ranked.postCount,
                   representative.map_image_id AS representativePostId,
                   representative.image_url AS imageUrl,
                   representative.thumbnail_url AS thumbnailUrl
            FROM ranked_places ranked
            JOIN LATERAL (
                SELECT mi.map_image_id,
                       mi.image_url,
                       mi.thumbnail_url
                FROM map_image mi
                WHERE mi.map_place_id = ranked.placeId
                  AND mi.visibility_status = 'ACTIVE'
                  AND mi.created_time IS NOT NULL
                  AND mi.created_time >= :periodStart
                ORDER BY mi.like_count DESC, mi.map_image_id ASC
                LIMIT 1
            ) representative ON TRUE
            ORDER BY ranked.likeCount DESC, ranked.placeId ASC
            """, nativeQuery = true)
    List<PlaceRankingProjection> findRankedPlaces(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("category") String category,
            @Param("local") boolean local,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") double radiusMeters,
            Pageable pageable
    );

    interface PlaceRankingProjection {
        Long getPlaceId();
        String getPlaceName();
        String getCategory();
        Double getLatitude();
        Double getLongitude();
        String getRegistrantUsername();
        Double getDistanceMeters();
        Long getLikeCount();
        Long getPostCount();
        Long getRepresentativePostId();
        String getImageUrl();
        String getThumbnailUrl();
    }
}
