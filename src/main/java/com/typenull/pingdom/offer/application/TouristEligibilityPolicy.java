package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TouristEligibilityPolicy {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;

    public void requireEligible(Long userId, LocalDateTime now) {
        requireEligible(userId, now, CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE);
    }

    public void requireEligible(
            Long userId,
            LocalDateTime now,
            CouponEligibilityPolicy eligibilityPolicy
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.TOURIST_ELIGIBILITY_REQUIRED));
        LocalDate today = now.toLocalDate();
        boolean activeUser = user.getRole() == UserRole.USER
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now);
        boolean hasActiveTravelSchedule = eligibilityPolicy == CouponEligibilityPolicy.PUBLIC
                || travelScheduleRepository
                        .existsByUser_IdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                userId,
                                TravelScheduleState.SCHEDULED,
                                today,
                                today
                        );
        boolean eligible = activeUser && hasActiveTravelSchedule;
        if (!eligible) {
            throw new OfferException(OfferErrorCode.TOURIST_ELIGIBILITY_REQUIRED);
        }
    }
}
