package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "밴 유저 목록 아이템")
public record AdminBannedUserItem(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,
        @Schema(description = "사용자명", example = "blockedUser01")
        String username,
        @Schema(description = "밴 처리 여부", example = "true")
        boolean banned,
        @Schema(description = "밴 유형", example = "PERMANENT")
        UserBanType banType,
        @Schema(description = "밴 처리 시각", example = "2026-06-07T13:30:00")
        LocalDateTime bannedAt,
        @Schema(description = "기간 밴 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime banExpiresAt
) {
}
