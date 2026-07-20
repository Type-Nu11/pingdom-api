package com.typenull.pingdom.moderation.api.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 전체 운영 현황 요약")
public record AdminDashboardSummaryResponse(
        @Schema(description = "전체 장소 수", example = "44")
        long placeCount,
        @Schema(description = "전체 게시글 수", example = "58")
        long postCount,
        @Schema(description = "처리 대기 신고 수", example = "5")
        long pendingReportCount,
        @Schema(description = "현재 밴 사용자 수", example = "6")
        long bannedUserCount
) {
}
