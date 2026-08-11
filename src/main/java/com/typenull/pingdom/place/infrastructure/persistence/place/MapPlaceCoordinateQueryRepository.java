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
}
