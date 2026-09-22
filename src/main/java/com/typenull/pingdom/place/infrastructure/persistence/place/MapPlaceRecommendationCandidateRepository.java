package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 추천 후보 수를 제한하기 위한 일반·위도대·날짜 변경선 통과 경계 조회를 제공합니다.
 * 정렬은 위경도 차이의 합에 의한 근사치이며 실제 미터 거리와 점수 계산은 추천 서비스가 수행합니다.
 */
public interface MapPlaceRecommendationCandidateRepository extends Repository<MapPlace, Long> {

    @Query("""
            SELECT m.id AS placeId, category AS category
            FROM MapPlace m
            JOIN m.touristCategories category
            WHERE m.id IN :placeIds
            """)
    List<PlaceTouristCategoryRow> findTouristCategoriesByPlaceIds(@Param("placeIds") List<Long> placeIds);

    interface PlaceTouristCategoryRow {
        Long getPlaceId();

        TouristCategory getCategory();
    }

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.operatingStatus = :operatingStatus
              AND m.discoveryStatus = :discoveryStatus
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
            @Param("operatingStatus") PlaceOperatingStatus operatingStatus,
            @Param("discoveryStatus") PlaceDiscoveryStatus discoveryStatus,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.operatingStatus = :operatingStatus
              AND m.discoveryStatus = :discoveryStatus
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
            @Param("operatingStatus") PlaceOperatingStatus operatingStatus,
            @Param("discoveryStatus") PlaceDiscoveryStatus discoveryStatus,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.operatingStatus = :operatingStatus
              AND m.discoveryStatus = :discoveryStatus
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
            @Param("operatingStatus") PlaceOperatingStatus operatingStatus,
            @Param("discoveryStatus") PlaceDiscoveryStatus discoveryStatus,
            Pageable pageable
    );
}
