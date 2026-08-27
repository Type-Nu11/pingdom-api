package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 장소 기본 조회와 상태·소유자·중복 판정을 위한 영속성 경계를 제공합니다.
public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    // Kakao 장소 식별자로 장소를 조회합니다.
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);

    // 현재 장소를 제외하고 동일한 Kakao 장소가 존재하는지 조회합니다.
    Optional<MapPlace> findByKakaoPlaceIdAndIdNot(String kakaoPlaceId, Long id);

    @Modifying
    @Query("""
            UPDATE MapPlace m
            SET m.registrant = :displayName
            WHERE m.userId = :userId
            """)
    // 탈퇴한 사용자가 등록한 장소의 표시 등록자명을 일괄 변경합니다.
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
    // 지정된 사용자들과 장소의 소유자 연결을 해제합니다.
    int clearUserIdByUserIds(@Param("userIds") Collection<Long> userIds);

    // Kakao 장소 식별자의 중복 여부를 확인합니다.
    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    // 장소가 특정 운영 상태인지 확인합니다.
    boolean existsByIdAndOperatingStatus(Long id, PlaceOperatingStatus operatingStatus);

    // 장소가 운영 상태와 발견 상태를 모두 만족하는지 확인합니다.
    boolean existsByIdAndOperatingStatusAndDiscoveryStatus(
            Long id,
            PlaceOperatingStatus operatingStatus,
            PlaceDiscoveryStatus discoveryStatus
    );

    // 이름과 주소가 일치하는 장소 후보를 조회합니다.
    List<MapPlace> findAllByNameAndAddress(String name, String address);

    // 좌표까지 일치하는 장소 후보를 하나 조회합니다.
    Optional<MapPlace> findFirstByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

    // 이름·주소·좌표가 모두 같은 장소의 존재 여부를 확인합니다.
    boolean existsByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

    List<MapPlace> findByRegionCodeIsNullOrderByIdAsc(Pageable pageable);

    // 주어진 기간에 생성된 장소 수를 집계합니다.
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT COUNT(m)
            FROM MapPlace m
            WHERE m.location IS NULL
            """)
    // 위치 정보가 없는 장소 수를 집계합니다.
    long countMissingLocation();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id = :placeId")
    // 장소를 비관적 쓰기 잠금으로 조회합니다.
    Optional<MapPlace> findByIdForUpdate(@Param("placeId") Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id IN :placeIds ORDER BY m.id ASC")
    // 여러 장소를 ID 순서로 비관적 쓰기 잠금 조회합니다.
    List<MapPlace> findAllByIdInForUpdate(@Param("placeIds") List<Long> placeIds);

    @Query("""
            SELECT DISTINCT m
            FROM MapPlace m
            LEFT JOIN FETCH m.regularOperatingHours
            WHERE m.id IN :placeIds
            """)
    // 장소와 정규 운영시간을 함께 조회해 지연 로딩을 방지합니다.
    List<MapPlace> findAllWithRegularOperatingHoursByIdIn(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT DISTINCT m
            FROM MapPlace m
            LEFT JOIN FETCH m.operatingExceptions exception
            LEFT JOIN FETCH exception.hours
            WHERE m.id IN :placeIds
            """)
    // 장소와 운영 예외 및 예외 시간을 함께 조회합니다.
    List<MapPlace> findAllWithOperatingExceptionsByIdIn(@Param("placeIds") Collection<Long> placeIds);
}
