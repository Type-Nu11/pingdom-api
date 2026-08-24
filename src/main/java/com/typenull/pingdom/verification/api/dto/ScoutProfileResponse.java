package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Scout 프로필 및 활동 자격 응답")
public record ScoutProfileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String introduction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoutProfileStatus profileStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long profileReviewedByAdminUserId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime profileReviewedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String profileStatusReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScoutActivityEligibilityStatus activityEligibilityStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime eligibleFrom,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime eligibleUntil,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long eligibilityReviewedByAdminUserId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime eligibilityReviewedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String eligibilityStatusReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {

    public static ScoutProfileResponse from(
            ScoutProfile profile,
            ScoutActivityEligibility eligibility
    ) {
        return new ScoutProfileResponse(
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getIntroduction(),
                profile.getStatus(),
                profile.getReviewedByAdminUserId(),
                profile.getReviewedAt(),
                profile.getStatusReason(),
                eligibility.getStatus(),
                eligibility.getEligibleFrom(),
                eligibility.getEligibleUntil(),
                eligibility.getReviewedByAdminUserId(),
                eligibility.getReviewedAt(),
                eligibility.getStatusReason(),
                profile.getCreatedAt(),
                profile.getUpdatedAt().isAfter(eligibility.getUpdatedAt())
                        ? profile.getUpdatedAt()
                        : eligibility.getUpdatedAt()
        );
    }
}
