package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import java.time.LocalDateTime;

public record AdminMerchantPlaceClaimListItemResponse(
        Long id,
        Long merchantOwnerUserId,
        Long placeId,
        MerchantPlaceClaimStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminMerchantPlaceClaimListItemResponse from(MerchantPlaceClaim claim) {
        return new AdminMerchantPlaceClaimListItemResponse(
                claim.getId(),
                claim.getMerchantOwnerUserId(),
                claim.getPlaceId(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
