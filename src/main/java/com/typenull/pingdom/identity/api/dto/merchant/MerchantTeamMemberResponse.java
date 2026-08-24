package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Merchant 장소 팀원 응답")
public record MerchantTeamMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantPlaceMemberRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantPlaceMemberStatus status,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {
    public static MerchantTeamMemberResponse from(MerchantPlaceMember member) {
        return new MerchantTeamMemberResponse(member.getId(), member.getPlaceId(), member.getUserId(),
                member.getRole(), member.getStatus(), member.getCreatedAt(), member.getUpdatedAt());
    }
}
