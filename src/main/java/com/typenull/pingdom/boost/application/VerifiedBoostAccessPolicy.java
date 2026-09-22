package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 장소 소유 행에 쓰기 잠금을 건 상태에서 현재 점주 역할·활성 프로필·인증 승인을 검사합니다.
 * 잠금 수명은 호출자의 트랜잭션에 따르며, 점주 계정과 인증 행 자체를 잠그지는 않습니다.
 */
@Component
@RequiredArgsConstructor
public class VerifiedBoostAccessPolicy {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

    /**
     * 장소 소유 행을 잠가 반환하기 전에 요청자의 현재 점주 역할·미탈퇴·미정지 상태와 프로필·본인·사업자 승인을 확인합니다.
     * 소유 행 부재, 자격 미충족 또는 다른 점주의 장소는 모두 PLACE_NOT_OWNED로 거절합니다.
     */
    public MerchantOwnerPlace requireOwnedPlaceForUpdate(Long ownerId, Long placeId, LocalDateTime now) {
        MerchantOwnerPlace ownerPlace = ownerPlaceRepository.findByPlaceIdForUpdate(placeId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PLACE_NOT_OWNED));
        User user = userRepository.findById(ownerId).orElse(null);
        boolean activeOwner = user != null
                && user.isMerchantOwner()
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now)
                && profileRepository.existsByUserIdAndStatus(ownerId, MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                        ownerId, MerchantVerificationStatus.APPROVED, MerchantVerificationStatus.APPROVED);
        if (!activeOwner || !ownerId.equals(ownerPlace.getMerchantOwnerUserId())) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.PLACE_NOT_OWNED);
        }
        return ownerPlace;
    }
}
