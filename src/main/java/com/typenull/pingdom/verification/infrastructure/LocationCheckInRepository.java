package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.LocationCheckInStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationCheckInRepository extends JpaRepository<LocationCheckIn, Long> {
    boolean existsByPlaceId(Long placeId);
    boolean existsByTouristUserIdAndPlaceIdAndCheckInDate(Long touristUserId, Long placeId, LocalDate checkInDate);
    boolean existsByTouristUserIdAndPlaceIdAndCheckInDateAndStatus(Long touristUserId, Long placeId,
            LocalDate checkInDate, LocationCheckInStatus status);
    Page<LocationCheckIn> findAllByTouristUserId(Long touristUserId, Pageable pageable);
    Optional<LocationCheckIn> findByIdAndTouristUserId(Long id, Long touristUserId);
}
