package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.time.LocalDateTime;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 최근 사용자 제재 항목")
public record AdminDashboardRecentUserSanctionItem(
        @Schema(description = "제재 이력 ID", example = "10")
        Long sanctionId,
        @Schema(description = "대상 사용자 ID", example = "5")
        Long targetUserId,
        @Schema(description = "대상 사용자명", example = "reported_user")
        String targetUsername,
        @Schema(description = "제재 처리 유형", example = "APPLIED")
        UserSanctionAction action,
        @Schema(description = "밴 유형", example = "PERMANENT")
        UserBanType banType,
        @Schema(description = "처리 사유", example = "부적절한 게시글입니다.")
        String reason,
        @Schema(description = "처리일", example = "2026-07-21T16:00:00")
        LocalDateTime processedAt
) {
}
