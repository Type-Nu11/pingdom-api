package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "밴 유저 상세 조회 응답")
public record AdminBannedUserDetailResponse(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,
        @Schema(description = "사용자명", example = "blockedUser01")
        String username,
        @Schema(description = "이메일", example = "blockedUser01@example.com")
        String email,
        @Schema(description = "출생연도", example = "1998")
        Integer birthYear,
        @Schema(description = "언어", example = "ko")
        String language,
        @Schema(description = "국가", example = "KR")
        String country,
        @Schema(description = "권한", example = "USER")
        String role,
        @Schema(description = "밴 여부", example = "true")
        boolean banned,
        @Schema(description = "밴 시각", example = "2026-06-07T13:30:00")
        LocalDateTime bannedAt,
        @Schema(description = "밴 유형", example = "PERMANENT")
        UserBanType banType,
        @Schema(description = "기간 밴 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime banExpiresAt,
        @Schema(description = "밴 사유", example = "반복적인 신고 누적")
        String banReason,
        @Schema(description = "가입 시각", example = "2026-06-01T10:00:00")
        LocalDateTime createdAt
) {
}
