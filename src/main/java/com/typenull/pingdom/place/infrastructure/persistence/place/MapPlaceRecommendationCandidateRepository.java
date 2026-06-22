package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MapPlaceRecommendationCandidateRepository extends Repository<MapPlace, Long> {

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND m.longitude BETWEEN :minLongitude AND :maxLongitude
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180.0 THEN ABS(m.longitude - :longitude)
                         ELSE 360.0 - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInBoundingBox(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180.0 THEN ABS(m.longitude - :longitude)
                         ELSE 360.0 - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInLatitudeBand(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND (m.longitude >= :westLongitude OR m.longitude <= :eastLongitude)
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180.0 THEN ABS(m.longitude - :longitude)
                         ELSE 360.0 - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInWrappedLongitudeBoundingBox(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("westLongitude") double westLongitude,
            @Param("eastLongitude") double eastLongitude,
            Pageable pageable
    );
}
