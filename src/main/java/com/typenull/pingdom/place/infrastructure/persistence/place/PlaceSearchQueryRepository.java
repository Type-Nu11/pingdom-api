package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PlaceSearchQueryRepository extends Repository<MapPlace, Long> {

    @Query(
            value = """
                    SELECT
                        mp.map_place_id AS id,
                        mp.place_name AS name,
                        mp.address AS address,
                        mp.category AS category,
                        mp.latitude AS latitude,
                        mp.longitude AS longitude,
                        CASE
                            WHEN :hasLocation = TRUE THEN
                                6371000.0 * 2.0 * ASIN(SQRT(
                                    POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                    + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                    * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                ))
                            ELSE NULL
                        END AS distanceMeters
                    FROM map_place mp
                    WHERE (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR (mp.category IS NOT NULL AND LOWER(TRIM(mp.category)) = :category))
                      AND (
                          :hasLocation = FALSE
                          OR (
                              mp.latitude BETWEEN :minLatitude AND :maxLatitude
                              AND (
                                  (:longitudeWrapped = FALSE AND mp.longitude BETWEEN :westLongitude AND :eastLongitude)
                                  OR (:longitudeWrapped = TRUE AND (mp.longitude >= :westLongitude OR mp.longitude <= :eastLongitude))
                              )
                              AND (
                                  6371000.0 * 2.0 * ASIN(SQRT(
                                      POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                      + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                      * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                  ))
                              ) <= :radiusMeters
                          )
                      )
                    ORDER BY
                        CASE
                            WHEN :sort = 'NEAREST' THEN
                                6371000.0 * 2.0 * ASIN(SQRT(
                                    POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                    + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                    * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                ))
                        END ASC,
                        mp.map_place_id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM map_place mp
                    WHERE (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR (mp.category IS NOT NULL AND LOWER(TRIM(mp.category)) = :category))
                      AND (
                          :hasLocation = FALSE
                          OR (
                              mp.latitude BETWEEN :minLatitude AND :maxLatitude
                              AND (
                                  (:longitudeWrapped = FALSE AND mp.longitude BETWEEN :westLongitude AND :eastLongitude)
                                  OR (:longitudeWrapped = TRUE AND (mp.longitude >= :westLongitude OR mp.longitude <= :eastLongitude))
                              )
                              AND (
                                  6371000.0 * 2.0 * ASIN(SQRT(
                                      POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                      + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                      * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                  ))
                              ) <= :radiusMeters
                          )
                      )
                    """,
            nativeQuery = true
    )
    Page<PlaceSearchProjection> searchPlaces(
            @Param("keywordPattern") String keywordPattern,
            @Param("category") String category,
            @Param("hasLocation") boolean hasLocation,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") Double radiusMeters,
            @Param("minLatitude") Double minLatitude,
            @Param("maxLatitude") Double maxLatitude,
            @Param("westLongitude") Double westLongitude,
            @Param("eastLongitude") Double eastLongitude,
            @Param("longitudeWrapped") boolean longitudeWrapped,
            @Param("sort") String sort,
            Pageable pageable
    );

    interface PlaceSearchProjection {
        Long getId();

        String getName();

        String getAddress();

        String getCategory();

        Double getLatitude();

        Double getLongitude();

        Double getDistanceMeters();
    }

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<MapPlace> findAutocompleteCandidates(@Param("keyword") String keyword, Pageable pageable);

    @Query(
            value = """
                    SELECT p
                    FROM MapBookmark b
                    JOIN MapPlace p ON p.id = b.placeId
                    WHERE b.userId = :userId
                    ORDER BY b.createdAt DESC, b.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(b)
                    FROM MapBookmark b
                    WHERE b.userId = :userId
                    """
    )
    Page<MapPlace> findBookmarkedPlacesByUserId(@Param("userId") Long userId, Pageable pageable);
}
