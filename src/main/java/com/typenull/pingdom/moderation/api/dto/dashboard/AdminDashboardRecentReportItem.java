package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.time.LocalDateTime;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 최근 신고 처리 항목")
public record AdminDashboardRecentReportItem(
        @Schema(description = "신고 ID", example = "10")
        Long reportId,
        @Schema(description = "신고 대상 게시글 ID", example = "22")
        Long reportedImageId,
        @Schema(description = "신고 대상 게시글 제목", example = "야경이 좋은 장소")
        String title,
        @Schema(description = "신고 상태", example = "ACCEPTED")
        PostReportStatus status,
        @Schema(description = "처리일", example = "2026-07-21T16:00:00")
        LocalDateTime processedAt,
        @Schema(description = "신고 생성일", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt
) {
}
