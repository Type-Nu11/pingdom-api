package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * 활성 팀원의 새 역할을 지정합니다. OWNER 부여 금지는 DTO가 아닌 도메인 상태 전이에서 검사합니다.
 */
public record MerchantTeamRoleUpdateRequest(@NotNull MerchantPlaceMemberRole role) {
}
