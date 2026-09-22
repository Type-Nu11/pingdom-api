package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

/**
 * 초대 대상·팀 역할과 선택적인 만료 시각입니다.
 * 만료 시각 미입력은 서비스에서 7일 뒤로 정하며 OWNER 초대와 현재 이하 만료 시각은 거부합니다.
 */
public record MerchantTeamInviteRequest(
        @NotNull @Positive Long inviteeUserId,
        @NotNull MerchantPlaceMemberRole role,
        LocalDateTime expiresAt
) {
}
