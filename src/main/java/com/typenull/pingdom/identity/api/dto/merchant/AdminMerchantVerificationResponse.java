package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자용 Merchant 신원 및 사업자 검증 응답")
public record AdminMerchantVerificationResponse(
        Long userId,
        String legalName,
        String businessName,
        String businessRegistrationNumber,
        MerchantVerificationStatus identityStatus,
        MerchantVerificationStatus businessStatus,
        @Schema(nullable = true) String reviewReason,
        @Schema(nullable = true) Long reviewedBy,
        @Schema(nullable = true) LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminMerchantVerificationResponse from(
            MerchantVerification verification,
            String businessRegistrationNumber
    ) {
        return new AdminMerchantVerificationResponse(
                verification.getUserId(),
                verification.getLegalName(),
                verification.getBusinessName(),
                businessRegistrationNumber,
                verification.getIdentityStatus(),
                verification.getBusinessStatus(),
                verification.getReviewReason(),
                verification.getReviewedBy(),
                verification.getReviewedAt(),
                verification.getCreatedAt(),
                verification.getUpdatedAt()
        );
    }
}
