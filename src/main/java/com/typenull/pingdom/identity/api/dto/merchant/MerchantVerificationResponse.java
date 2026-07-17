package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 Merchant 신원 및 사업자 검증 상태")
public record MerchantVerificationResponse(
        Long userId,
        String legalName,
        String businessName,
        @Schema(example = "123-45-*****") String maskedBusinessRegistrationNumber,
        MerchantVerificationStatus identityStatus,
        MerchantVerificationStatus businessStatus,
        @Schema(nullable = true) String reviewReason,
        @Schema(nullable = true) LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MerchantVerificationResponse from(
            MerchantVerification verification,
            String businessRegistrationNumber
    ) {
        return new MerchantVerificationResponse(
                verification.getUserId(),
                verification.getLegalName(),
                verification.getBusinessName(),
                mask(businessRegistrationNumber),
                verification.getIdentityStatus(),
                verification.getBusinessStatus(),
                verification.getReviewReason(),
                verification.getReviewedAt(),
                verification.getCreatedAt(),
                verification.getUpdatedAt()
        );
    }

    public static String mask(String registrationNumber) {
        if (registrationNumber == null || registrationNumber.length() != 10) {
            return "*****";
        }
        return registrationNumber.substring(0, 3)
                + "-"
                + registrationNumber.substring(3, 5)
                + "-*****";
    }
}
