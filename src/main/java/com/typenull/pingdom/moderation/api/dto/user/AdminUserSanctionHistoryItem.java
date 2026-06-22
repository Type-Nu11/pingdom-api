package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 사용자 제재 이력 아이템")
public record AdminUserSanctionHistoryItem(
        @Schema(description = "제재 이력 ID", example = "1")
        Long historyId,
        @Schema(description = "대상 사용자 ID", example = "7")
        Long targetUserId,
        @Schema(description = "대상 사용자명", example = "blockedUser01")
        String targetUsername,
        @Schema(description = "제재 유형", example = "TEMPORARY")
        UserBanType banType,
        @Schema(description = "처리 상태", example = "APPLIED")
        UserSanctionAction action,
        @Schema(description = "처리 사유", example = "반복적인 신고 누적")
        String reason,
        @Schema(description = "제재 시작 시각", example = "2026-06-22T10:15:30")
        LocalDateTime startedAt,
        @Schema(description = "제재 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime endedAt,
        @Schema(description = "처리 관리자 ID", example = "1")
        Long adminUserId,
        @Schema(description = "처리 관리자명", example = "admin")
        String adminUsername,
        @Schema(description = "처리 시각", example = "2026-06-22T10:15:30")
        LocalDateTime processedAt
) {
}
