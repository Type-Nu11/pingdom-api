package com.typenull.pingdom.moderation.api.dto.audit;

import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 감사 로그 아이템")
public record AdminAuditLogItem(
        @Schema(description = "감사 로그 ID", example = "1")
        Long auditLogId,
        @Schema(description = "작업 관리자 ID", example = "1")
        Long actorUserId,
        @Schema(description = "작업 관리자명", example = "admin")
        String actorUsername,
        @Schema(description = "작업 유형", example = "USER_BAN_APPLIED")
        AdminAuditAction action,
        @Schema(description = "대상 유형", example = "USER")
        AdminAuditTargetType targetType,
        @Schema(description = "대상 ID", example = "7")
        String targetId,
        @Schema(description = "작업 사유", example = "반복적인 신고 누적")
        String reason,
        @Schema(description = "작업 전 상태 JSON")
        String beforeState,
        @Schema(description = "작업 후 상태 JSON")
        String afterState,
        @Schema(description = "요청 ID", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
        String requestId,
        @Schema(description = "감사 로그 생성 시각", example = "2026-06-24T10:15:30")
        LocalDateTime createdAt
) {
}
