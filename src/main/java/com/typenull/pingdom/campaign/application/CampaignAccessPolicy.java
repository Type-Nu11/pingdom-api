package com.typenull.pingdom.campaign.application;

import com.typenull.pingdom.campaign.domain.exception.CampaignErrorCode;
import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 브랜드·캠페인 변경 시 현재 점주 역할, 탈퇴·정지 상태, 활성 프로필과 본인/사업자 승인을 검사합니다.
 * 장소 지정 작업에는 현재 소유 관계까지 요구하며, 행 잠금 없이 조회하므로 변경 경합 제어는 호출자가 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class CampaignAccessPolicy {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

    /**
     * 지정 시점에 탈퇴·정지되지 않은 점주이며 ACTIVE 프로필과 본인·사업자 승인 정보를 갖췄는지 검사합니다.
     * 자격 미충족은 PLACE_NOT_OWNED로 거절하며 특정 장소 소유 관계는 별도 검사에 맡깁니다.
     */
    public void requireActiveOwner(Long ownerId, LocalDateTime now) {
        User user = userRepository.findById(ownerId).orElse(null);
        boolean active = user != null
                && user.isMerchantOwner()
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now)
                && profileRepository.existsByUserIdAndStatus(ownerId, MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                        ownerId,
                        MerchantVerificationStatus.APPROVED,
                        MerchantVerificationStatus.APPROVED
                );
        if (!active) {
            throw new CampaignException(CampaignErrorCode.PLACE_NOT_OWNED);
        }
    }

    public void requireOwnedPlace(Long ownerId, Long placeId, LocalDateTime now) {
        requireActiveOwner(ownerId, now);
        if (!ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, ownerId)) {
            throw new CampaignException(CampaignErrorCode.PLACE_NOT_OWNED);
        }
    }
}
