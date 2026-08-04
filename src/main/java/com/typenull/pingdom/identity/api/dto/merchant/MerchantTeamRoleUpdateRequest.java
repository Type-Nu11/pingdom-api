package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import jakarta.validation.constraints.NotNull;

public record MerchantTeamRoleUpdateRequest(@NotNull MerchantPlaceMemberRole role) {
}
