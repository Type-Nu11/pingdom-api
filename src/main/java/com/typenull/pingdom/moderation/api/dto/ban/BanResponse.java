package com.typenull.pingdom.moderation.api.dto.ban;

import com.typenull.pingdom.identity.domain.UserBanType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 밴 처리 응답")
public record BanResponse(
        @Schema(description = "밴 처리된 사용자 ID", example = "7")
        Long userId,
        @Schema(description = "밴 여부", example = "true")
        boolean banned,
        @Schema(description = "밴 처리 시각", example = "2026-05-21T10:15:30")
        LocalDateTime bannedAt,
        @Schema(description = "밴 사유", example = "반복적인 신고 누적")
        String reason,
        @Schema(description = "밴 유형", example = "TEMPORARY")
        UserBanType banType,
        @Schema(description = "기간 밴 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime banExpiresAt
) {
}
