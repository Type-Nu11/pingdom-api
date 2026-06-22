package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 사용자 제재 상태 조회 응답")
public record AdminUserSanctionStatusResponse(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,
        @Schema(description = "사용자명", example = "blockedUser01")
        String username,
        @Schema(description = "현재 제재 여부", example = "true")
        boolean banned,
        @Schema(description = "제재 유형", example = "TEMPORARY")
        UserBanType banType,
        @Schema(description = "제재 시작 시각", example = "2026-06-22T10:15:30")
        LocalDateTime bannedAt,
        @Schema(description = "제재 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime banExpiresAt,
        @Schema(description = "제재 사유", example = "반복적인 신고 누적")
        String banReason
) {
}
