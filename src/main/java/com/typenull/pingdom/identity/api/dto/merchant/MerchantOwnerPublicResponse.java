package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소에 연결된 Merchant Owner 공개 프로필")
public record MerchantOwnerPublicResponse(
        Long userId,
        String businessName,
        String displayName,
        @Schema(nullable = true) String description,
        String contactEmail,
        String contactPhone
) {
    public static MerchantOwnerPublicResponse from(MerchantOwnerProfile profile) {
        return new MerchantOwnerPublicResponse(
                profile.getUserId(),
                profile.getBusinessName(),
                profile.getDisplayName(),
                profile.getDescription(),
                profile.getContactEmail(),
                profile.getContactPhone()
        );
    }
}
