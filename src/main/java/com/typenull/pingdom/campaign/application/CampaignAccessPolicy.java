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

@Component
@RequiredArgsConstructor
public class CampaignAccessPolicy {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

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
