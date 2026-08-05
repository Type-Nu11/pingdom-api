package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Scout 프로필 및 활동 자격 응답")
public record ScoutProfileResponse(
        Long userId,
        String displayName,
        @Schema(nullable = true) String introduction,
        ScoutProfileStatus profileStatus,
        @Schema(nullable = true) Long profileReviewedByAdminUserId,
        @Schema(nullable = true) LocalDateTime profileReviewedAt,
        @Schema(nullable = true) String profileStatusReason,
        ScoutActivityEligibilityStatus activityEligibilityStatus,
        @Schema(nullable = true) LocalDateTime eligibleFrom,
        @Schema(nullable = true) LocalDateTime eligibleUntil,
        @Schema(nullable = true) Long eligibilityReviewedByAdminUserId,
        @Schema(nullable = true) LocalDateTime eligibilityReviewedAt,
        @Schema(nullable = true) String eligibilityStatusReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
