package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자용 상점 장소 Claim 요청")
public record AdminMerchantPlaceClaimResponse(
        Long id,
        Long merchantOwnerUserId,
        Long placeId,
        MerchantPlaceClaimType claimType,
        @Schema(nullable = true) Long previousOwnerUserId,
        MerchantPlaceClaimStatus status,
        String claimReason,
        @Schema(nullable = true) String reviewReason,
        @Schema(nullable = true) Long reviewedBy,
        long version,
        @Schema(nullable = true) LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminMerchantPlaceClaimResponse from(MerchantPlaceClaim claim) {
        return new AdminMerchantPlaceClaimResponse(
                claim.getId(),
                claim.getMerchantOwnerUserId(),
                claim.getPlaceId(),
                claim.getClaimType(),
                claim.getPreviousOwnerUserId(),
                claim.getStatus(),
                claim.getClaimReason(),
                claim.getReviewReason(),
                claim.getReviewedBy(),
                claim.getVersion(),
                claim.getReviewedAt(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
