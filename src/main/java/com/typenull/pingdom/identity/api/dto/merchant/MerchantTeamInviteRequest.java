package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record MerchantTeamInviteRequest(
        @NotNull @Positive Long inviteeUserId,
        @NotNull MerchantPlaceMemberRole role,
        LocalDateTime expiresAt
) {
}
