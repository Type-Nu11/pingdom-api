package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 장소 기본 조회와 상태·소유자·중복 판정을 위한 영속성 경계를 제공합니다. */
public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);

    Optional<MapPlace> findByKakaoPlaceIdAndIdNot(String kakaoPlaceId, Long id);

    @Modifying
    @Query("""
            UPDATE MapPlace m
            SET m.registrant = :displayName
            WHERE m.userId = :userId
            """)
    int updateRegistrantByUserId(
            @Param("userId") Long userId,
            @Param("displayName") String displayName
    );

    @Modifying
    @Query("""
            UPDATE MapPlace m
            SET m.userId = NULL
            WHERE m.userId IN :userIds
            """)
    int clearUserIdByUserIds(@Param("userIds") Collection<Long> userIds);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    boolean existsByIdAndOperatingStatus(Long id, PlaceOperatingStatus operatingStatus);

    boolean existsByIdAndOperatingStatusAndDiscoveryStatus(
            Long id,
            PlaceOperatingStatus operatingStatus,
            PlaceDiscoveryStatus discoveryStatus
    );

    List<MapPlace> findAllByNameAndAddress(String name, String address);

    Optional<MapPlace> findFirstByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

    boolean existsByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT COUNT(m)
            FROM MapPlace m
            WHERE m.location IS NULL
            """)
    long countMissingLocation();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id = :placeId")
    Optional<MapPlace> findByIdForUpdate(@Param("placeId") Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id IN :placeIds ORDER BY m.id ASC")
    List<MapPlace> findAllByIdInForUpdate(@Param("placeIds") List<Long> placeIds);

    @Query("""
            SELECT DISTINCT m
            FROM MapPlace m
            LEFT JOIN FETCH m.regularOperatingHours
            WHERE m.id IN :placeIds
            """)
    List<MapPlace> findAllWithRegularOperatingHoursByIdIn(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT DISTINCT m
            FROM MapPlace m
            LEFT JOIN FETCH m.operatingExceptions exception
            LEFT JOIN FETCH exception.hours
            WHERE m.id IN :placeIds
            """)
    List<MapPlace> findAllWithOperatingExceptionsByIdIn(@Param("placeIds") Collection<Long> placeIds);
}
