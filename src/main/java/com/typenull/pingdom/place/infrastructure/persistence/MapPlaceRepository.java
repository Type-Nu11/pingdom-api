package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.MapPlace;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
            """)
    List<MapPlace> findAllWithCoordinates();

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND m.longitude BETWEEN :minLongitude AND :maxLongitude
            ORDER BY ABS(m.latitude - :latitude) + ABS(m.longitude - :longitude)
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

    @Query("SELECT m FROM MapPlace m WHERE (:keyword IS NULL OR :keyword = '' OR m.name LIKE %:keyword%)")
    Page<MapPlace> findByNameContaining(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id = :placeId")
    Optional<MapPlace> findByIdForUpdate(@Param("placeId") Long placeId);
}
