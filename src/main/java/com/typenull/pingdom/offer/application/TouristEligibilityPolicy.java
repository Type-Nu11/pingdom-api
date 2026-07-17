package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.TOURIST_ELIGIBILITY_REQUIRED));
        LocalDate today = now.toLocalDate();
        boolean eligible = user.getRole() == UserRole.USER
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now)
                && travelScheduleRepository
                .existsByUser_IdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        userId,
                        TravelScheduleState.SCHEDULED,
                        today,
                        today
                );
        if (!eligible) {
            throw new OfferException(OfferErrorCode.TOURIST_ELIGIBILITY_REQUIRED);
        }
    }
}
