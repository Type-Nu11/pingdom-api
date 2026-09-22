package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 현재 점주 역할·활성 프로필·본인 및 사업자 승인·장소 소유 관계를 함께 검사.
 * 탈퇴 또는 유효한 정지 상태의 사용자는 제외하며, 조회와 검사만 수행하므로 쓰기 경합 제어는 호출자가 담당.
 */
@Component
@RequiredArgsConstructor
public class MerchantOfferAccessPolicy {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

    public void requireOwnedPlace(Long merchantOwnerUserId, Long placeId, LocalDateTime now) {
        if (!isActiveOwnerOfPlace(merchantOwnerUserId, placeId, now)) {
            throw new OfferException(OfferErrorCode.PLACE_NOT_OWNED);
        }
    }

    /**
     * 지정 시점에 탈퇴·정지되지 않은 점주의 역할·ACTIVE 프로필·본인 및 사업자 승인과 장소 소유 관계를 함께 검사.
     * 회원 부재나 자격·소유 조건 미충족은 예외 대신 false를 반환.
     */
    public boolean isActiveOwnerOfPlace(Long merchantOwnerUserId, Long placeId, LocalDateTime now) {
        User user = userRepository.findById(merchantOwnerUserId)
                .orElse(null);
        if (user == null) {
            return false;
        }
        boolean activeMerchantOwner = user.isMerchantOwner()
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now)
                && profileRepository.existsByUserIdAndStatus(merchantOwnerUserId, MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                        merchantOwnerUserId,
                        MerchantVerificationStatus.APPROVED,
                        MerchantVerificationStatus.APPROVED
                );
        return activeMerchantOwner
                && ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, merchantOwnerUserId);
    }
}
