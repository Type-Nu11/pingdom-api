package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.LocationCheckIn;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationCheckInRepository extends JpaRepository<LocationCheckIn, Long> {
    boolean existsByTouristUserIdAndPlaceIdAndCheckInDate(Long touristUserId, Long placeId, LocalDate checkInDate);
    Page<LocationCheckIn> findAllByTouristUserId(Long touristUserId, Pageable pageable);
}
