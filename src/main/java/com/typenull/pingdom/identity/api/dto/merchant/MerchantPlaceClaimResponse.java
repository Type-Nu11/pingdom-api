package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 상점 장소 Claim 요청")
public record MerchantPlaceClaimResponse(
        Long id,
        Long placeId,
        MerchantPlaceClaimType claimType,
        MerchantPlaceClaimStatus status,
        String reason,
        @Schema(nullable = true) String reviewReason,
        @Schema(nullable = true) LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MerchantPlaceClaimResponse from(MerchantPlaceClaim claim) {
        return new MerchantPlaceClaimResponse(
                claim.getId(),
                claim.getPlaceId(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getClaimReason(),
                claim.getReviewReason(),
                claim.getReviewedAt(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
