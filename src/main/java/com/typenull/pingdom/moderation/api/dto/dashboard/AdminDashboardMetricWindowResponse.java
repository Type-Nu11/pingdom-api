package com.typenull.pingdom.moderation.api.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 대시보드 기간별 운영 변화 지표")
public record AdminDashboardMetricWindowResponse(
        @Schema(description = "집계 기간 코드", allowableValues = {"TODAY", "LAST_7_DAYS"}, example = "TODAY")
        String period,
        @Schema(description = "집계 시작 시각", example = "2026-07-21T00:00:00")
        LocalDateTime startedAt,
        @Schema(description = "집계 종료 시각", example = "2026-07-21T15:30:00")
        LocalDateTime endedAt,
        @Schema(description = "기간 내 장소 등록 수", example = "3")
        long placeRegistrationCount,
        @Schema(description = "기간 내 게시글 등록 수", example = "7")
        long postRegistrationCount
) {
}
