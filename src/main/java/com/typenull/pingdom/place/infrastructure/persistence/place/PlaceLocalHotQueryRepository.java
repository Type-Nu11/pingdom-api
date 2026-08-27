package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PlaceLocalHotQueryRepository extends Repository<MapPlace, Long> {

    @Query(value = """
            WITH local_places AS (
                SELECT map_place.map_place_id AS placeId,
                       map_place.place_name AS placeName,
                       map_place.category AS category,
                       map_place.address AS address,
                       map_place.latitude AS latitude,
                       map_place.longitude AS longitude,
                       COUNT(bookmark.id) AS bookmarkCount
                FROM map_place map_place
                LEFT JOIN map_bookmark bookmark ON bookmark.place_id = map_place.map_place_id
                WHERE map_place.region_code = :regionCode
                  AND map_place.discovery_status = 'VISIBLE'
                  AND map_place.operating_status = 'OPERATING'
                GROUP BY map_place.map_place_id, map_place.place_name, map_place.category,
                         map_place.address, map_place.latitude, map_place.longitude
            )
            SELECT local_places.placeId AS placeId,
                   local_places.placeName AS placeName,
                   local_places.category AS category,
                   local_places.address AS address,
                   local_places.latitude AS latitude,
                   local_places.longitude AS longitude,
                   COALESCE(map_place.image_url, image.image_url) AS imageUrl,
                   local_places.bookmarkCount AS bookmarkCount,
                   EXISTS (
                       SELECT 1
                       FROM map_bookmark user_bookmark
                       WHERE user_bookmark.place_id = local_places.placeId
                         AND user_bookmark.user_id = :userId
                   ) AS bookmarked
            FROM local_places
            JOIN map_place map_place ON map_place.map_place_id = local_places.placeId
            LEFT JOIN LATERAL (
                SELECT map_image.image_url
                FROM map_image
                WHERE map_image.map_place_id = local_places.placeId
                  AND map_image.visibility_status = 'ACTIVE'
                ORDER BY map_image.created_time DESC NULLS LAST, map_image.map_image_id DESC
                LIMIT 1
            ) image ON TRUE
            ORDER BY local_places.bookmarkCount DESC, local_places.placeId DESC
            """, nativeQuery = true)
    List<PlaceLocalHotProjection> findLocalHotPlaces(
            @Param("regionCode") String regionCode,
            @Param("userId") long userId,
            Pageable pageable
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM map_place map_place
            WHERE map_place.region_code = :regionCode
              AND map_place.discovery_status = 'VISIBLE'
              AND map_place.operating_status = 'OPERATING'
            """, nativeQuery = true)
    long countLocalHotPlaces(@Param("regionCode") String regionCode);

    interface PlaceLocalHotProjection {
        Long getPlaceId();
        String getPlaceName();
        String getCategory();
        String getAddress();
        Double getLatitude();
        Double getLongitude();
        String getImageUrl();
        long getBookmarkCount();
        boolean getBookmarked();
    }
}
