package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import java.time.LocalDateTime;

public record AdminMerchantVerificationListItemResponse(
        Long userId,
        String legalName,
        String businessName,
        String maskedBusinessRegistrationNumber,
        MerchantVerificationStatus identityStatus,
        MerchantVerificationStatus businessStatus,
        LocalDateTime updatedAt
) {
    public static AdminMerchantVerificationListItemResponse from(
            MerchantVerification verification,
            String businessRegistrationNumber
    ) {
        return new AdminMerchantVerificationListItemResponse(
                verification.getUserId(),
                verification.getLegalName(),
                verification.getBusinessName(),
                MerchantVerificationResponse.mask(businessRegistrationNumber),
                verification.getIdentityStatus(),
                verification.getBusinessStatus(),
                verification.getUpdatedAt()
        );
    }
}
