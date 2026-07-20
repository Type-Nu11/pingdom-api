package com.typenull.pingdom.availability.application;

import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.*;
import com.typenull.pingdom.identity.domain.repository.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
