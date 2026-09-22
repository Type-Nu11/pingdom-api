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

/**
 * 탈퇴·정지되지 않은 일반 사용자에게 쿠폰 발급을 허용하는 자격 정책.
 * PUBLIC은 여행 일정 조건만 생략하고, 기본 정책은 오늘을 양 끝 포함하는 SCHEDULED 일정을 요구.
 */
@Component
@RequiredArgsConstructor
public class TouristEligibilityPolicy {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;

    public void requireEligible(Long userId, LocalDateTime now) {
        requireEligible(userId, now, CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE);
    }

    /**
     * 탈퇴·정지되지 않은 일반 사용자에게 정책에 따른 쿠폰 발급 자격이 있는지 확인.
     * PUBLIC은 여행 일정 조건을 생략하고 그 외에는 오늘을 포함하는 SCHEDULED 일정이 필요하며 미충족 시 관광객 자격 오류 발생.
     */
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
