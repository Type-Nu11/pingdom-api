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
