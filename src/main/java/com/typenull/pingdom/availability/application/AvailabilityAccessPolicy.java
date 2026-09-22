package com.typenull.pingdom.availability.application;

import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.*;
import com.typenull.pingdom.identity.domain.repository.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 슬롯과 예약 관련 서비스가 사용할 현재 점주 자격과 장소 소유권을 검사.
 * 활성 프로필·승인된 본인/사업자 인증·탈퇴/정지 상태를 함께 확인하며 조회 대상 행 잠금은 호출 서비스가 담당.
 */
@Component
@RequiredArgsConstructor
public class AvailabilityAccessPolicy {
    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

    public void requireOwnedPlace(Long userId, Long placeId, LocalDateTime now) {
        requireActiveMerchantOwner(userId, now);
        if (!ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, userId)) {
            throw new AvailabilityException(AvailabilityErrorCode.PLACE_NOT_OWNED);
        }
    }

    public void requireActiveMerchantOwner(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElse(null);
        boolean allowed = user != null && user.isMerchantOwner() && !user.isWithdrawn() && !user.isCurrentlyBanned(now)
                && profileRepository.existsByUserIdAndStatus(userId, MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(userId,
                        MerchantVerificationStatus.APPROVED, MerchantVerificationStatus.APPROVED);
        if (!allowed) throw new AvailabilityException(AvailabilityErrorCode.PLACE_NOT_OWNED);
    }
}
