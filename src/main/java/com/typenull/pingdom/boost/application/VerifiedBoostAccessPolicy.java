package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
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
public class VerifiedBoostAccessPolicy {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;

    public void requireOwnedPlace(Long ownerId, Long placeId, LocalDateTime now) {
        User user = userRepository.findById(ownerId).orElse(null);
        boolean activeOwner = user != null
                && user.isMerchantOwner()
                && !user.isWithdrawn()
                && !user.isCurrentlyBanned(now)
                && profileRepository.existsByUserIdAndStatus(ownerId, MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                        ownerId, MerchantVerificationStatus.APPROVED, MerchantVerificationStatus.APPROVED);
        if (!activeOwner || !ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, ownerId)) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.PLACE_NOT_OWNED);
        }
    }
}
