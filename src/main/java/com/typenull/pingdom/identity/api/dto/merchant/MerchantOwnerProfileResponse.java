package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOnboardingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Merchant Owner 프로필 응답")
public record MerchantOwnerProfileResponse(
        Long userId,
        String businessName,
        String displayName,
        @Schema(nullable = true) String description,
        String contactEmail,
        String contactPhone,
        MerchantOwnerStatus status,
        MerchantOnboardingStatus onboardingStatus,
        Integer onboardingCompletionRate,
        @Schema(nullable = true) LocalDateTime onboardingCompletedAt,
        @Schema(nullable = true) Long reviewedBy,
        @Schema(nullable = true) LocalDateTime reviewedAt,
        @Schema(nullable = true) String reviewReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Long> placeIds
) {
    public static MerchantOwnerProfileResponse from(MerchantOwnerProfile profile, List<Long> placeIds) {
        return new MerchantOwnerProfileResponse(
                profile.getUserId(),
                profile.getBusinessName(),
                profile.getDisplayName(),
                profile.getDescription(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getStatus(),
                profile.getOnboardingStatus(),
                profile.getOnboardingCompletionRate(),
                profile.getOnboardingCompletedAt(),
                profile.getReviewedBy(),
                profile.getReviewedAt(),
                profile.getReviewReason(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                placeIds
        );
    }
}
