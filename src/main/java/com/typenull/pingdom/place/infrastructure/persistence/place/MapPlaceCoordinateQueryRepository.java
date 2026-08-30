package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MapPlaceCoordinateQueryRepository extends Repository<MapPlace, Long> {

    @Query(
            value = """
                    SELECT m
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    ORDER BY m.latitude ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(m)
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    """
    )
    Page<MapPlace> findCoordinatePage(Pageable pageable);

    @Query(
            value = """
                    SELECT candidate.*
                    FROM map_place target
                    JOIN map_place candidate
                      ON candidate.map_place_id <> target.map_place_id
                    WHERE target.map_place_id = :placeId
                      AND target.location IS NOT NULL
                      AND candidate.location IS NOT NULL
                      AND ST_DWithin(
                          candidate.location::geography,
                          target.location::geography,
                          :radiusMeters,
                          false
                      )
                    ORDER BY candidate.map_place_id
                    """,
            nativeQuery = true
    )
    List<MapPlace> findNearbyPlaces(
            @Param("placeId") Long placeId,
            @Param("radiusMeters") double radiusMeters
    );

    @Query(
            value = """
                    SELECT candidate.map_place_id AS placeId,
                           candidate.place_name AS name,
                           candidate.category AS category,
                           candidate.address AS address,
                           ST_Distance(
                               candidate.location::geography,
                               ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                               false
                           ) AS distanceMeters
                    FROM map_place candidate
                    WHERE candidate.location IS NOT NULL
                      AND LOWER(TRIM(candidate.category)) = LOWER(TRIM(:category))
                      AND candidate.operating_status = 'OPERATING'
                      AND candidate.discovery_status = 'VISIBLE'
                      AND ST_DWithin(
                          candidate.location::geography,
                          ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                          :radiusMeters,
                          false
                      )
                      AND ST_Distance(
                          candidate.location::geography,
                          ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                          false
                      ) > 1
                    ORDER BY distanceMeters ASC, candidate.map_place_id ASC
                    """,
            nativeQuery = true
    )
    List<NearbyCategoryPlace> findNearbyPlacesByCategory(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("category") String category,
            @Param("radiusMeters") double radiusMeters,
            Pageable pageable
    );

    interface NearbyCategoryPlace {
        Long getPlaceId();

        String getName();

        String getCategory();

        String getAddress();

        Double getDistanceMeters();
    }
}
