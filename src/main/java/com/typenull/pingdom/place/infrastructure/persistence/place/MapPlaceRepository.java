package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id = :placeId")
    Optional<MapPlace> findByIdForUpdate(@Param("placeId") Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id IN :placeIds ORDER BY m.id ASC")
    List<MapPlace> findAllByIdInForUpdate(@Param("placeIds") List<Long> placeIds);
}
