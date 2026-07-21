package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 처리 필요 항목")
public record AdminDashboardPendingItem(
        @Schema(description = "처리 대상 유형", example = "POST_REPORT")
        String type,
        @Schema(description = "처리 대상 ID", example = "10")
        Long targetId,
        @Schema(description = "처리 대상 제목", example = "야경이 좋은 장소")
        String title,
        @Schema(description = "처리 대상 상태", example = "PENDING")
        String status,
        @Schema(description = "생성일", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt
) {
}
