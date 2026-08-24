package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Merchant 장소 팀원 초대 응답")
public record MerchantTeamInvitationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inviteeUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantPlaceMemberRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantPlaceInvitationStatus status,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime expiresAt,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt
) {
    public static MerchantTeamInvitationResponse from(MerchantPlaceInvitation invitation) {
        return new MerchantTeamInvitationResponse(invitation.getId(), invitation.getPlaceId(), invitation.getInviteeUserId(),
                invitation.getRole(), invitation.getStatus(), invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
