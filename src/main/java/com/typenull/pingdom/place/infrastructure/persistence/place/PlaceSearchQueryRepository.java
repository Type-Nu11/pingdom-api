package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapBookmark;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import java.time.LocalDateTime;
import java.util.Collection;
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
                        mp.english_name AS englishName,
                        mp.address AS address,
                        mp.road_address AS roadAddress,
                        mp.jibun_address AS jibunAddress,
                        mp.postal_code AS postalCode,
                        mp.geocoding_source AS geocodingSource,
                        mp.operating_status AS operatingStatus,
                        mp.operating_status_checked_at AS operatingStatusCheckedAt,
                        mp.category AS category,
                        mp.tourist_summary AS touristSummary,
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
                    WHERE mp.latitude BETWEEN -90.0 AND 90.0
                      AND mp.longitude BETWEEN -180.0 AND 180.0
                      AND mp.operating_status = :operatingStatus
                      AND mp.discovery_status = :discoveryStatus
                      AND (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.english_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.road_address) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.jibun_address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR (mp.category IS NOT NULL AND LOWER(TRIM(mp.category)) = :category))
                      AND (:touristCategory IS NULL OR EXISTS (
                          SELECT 1
                          FROM map_place_tourist_category mptc
                          WHERE mptc.map_place_id = mp.map_place_id
                            AND mptc.tourist_category = :touristCategory
                      ))
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
                        CASE WHEN :sort = 'POPULAR' THEN COALESCE(mp.photo_count, 0) END DESC,
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
                    WHERE mp.latitude BETWEEN -90.0 AND 90.0
                      AND mp.longitude BETWEEN -180.0 AND 180.0
                      AND mp.operating_status = :operatingStatus
                      AND mp.discovery_status = :discoveryStatus
                      AND (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.english_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.road_address) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.jibun_address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR (mp.category IS NOT NULL AND LOWER(TRIM(mp.category)) = :category))
                      AND (:touristCategory IS NULL OR EXISTS (
                          SELECT 1
                          FROM map_place_tourist_category mptc
                          WHERE mptc.map_place_id = mp.map_place_id
                            AND mptc.tourist_category = :touristCategory
                      ))
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
            @Param("touristCategory") String touristCategory,
            @Param("operatingStatus") String operatingStatus,
            @Param("discoveryStatus") String discoveryStatus,
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

        String getEnglishName();

        String getAddress();

        String getRoadAddress();

        String getJibunAddress();

        String getPostalCode();

        GeocodingSource getGeocodingSource();

        PlaceOperatingStatus getOperatingStatus();

        LocalDateTime getOperatingStatusCheckedAt();

        String getCategory();

        String getTouristSummary();

        Double getLatitude();

        Double getLongitude();

        Double getDistanceMeters();
    }

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.operatingStatus = :operatingStatus
              AND m.discoveryStatus = :discoveryStatus
              AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.englishName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.roadAddress) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.jibunAddress) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.category) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    List<MapPlace> findAutocompleteCandidates(
            @Param("keyword") String keyword,
            @Param("operatingStatus") PlaceOperatingStatus operatingStatus,
            @Param("discoveryStatus") PlaceDiscoveryStatus discoveryStatus,
            Pageable pageable
    );

    @Query("""
            SELECT m.id AS placeId, touristCategory AS touristCategory
            FROM MapPlace m
            JOIN m.touristCategories touristCategory
            WHERE m.id IN :placeIds
            ORDER BY m.id, touristCategory
            """)
    List<PlaceTouristCategoryProjection> findTouristCategoriesByPlaceIds(
            @Param("placeIds") Collection<Long> placeIds
    );

    interface PlaceTouristCategoryProjection {
        Long getPlaceId();

        TouristCategory getTouristCategory();
    }

    @Query(
            value = """
                    SELECT p
                    FROM MapBookmark b
                    JOIN MapPlace p ON p.id = b.placeId
                    WHERE b.userId = :userId
                      AND p.operatingStatus = :operatingStatus
                      AND p.discoveryStatus = :discoveryStatus
                    ORDER BY b.createdAt DESC, b.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(b)
                    FROM MapBookmark b
                    JOIN MapPlace p ON p.id = b.placeId
                    WHERE b.userId = :userId
                      AND p.operatingStatus = :operatingStatus
                      AND p.discoveryStatus = :discoveryStatus
                    """
    )
    Page<MapPlace> findBookmarkedPlacesByUserId(
            @Param("userId") Long userId,
            @Param("operatingStatus") PlaceOperatingStatus operatingStatus,
            @Param("discoveryStatus") PlaceDiscoveryStatus discoveryStatus,
            Pageable pageable
    );
}
