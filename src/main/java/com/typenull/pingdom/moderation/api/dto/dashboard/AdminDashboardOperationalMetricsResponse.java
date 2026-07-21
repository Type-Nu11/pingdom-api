package com.typenull.pingdom.moderation.api.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 대시보드 추가 운영 지표")
public record AdminDashboardOperationalMetricsResponse(
        @Schema(description = "오늘 등록 변화 지표")
        AdminDashboardMetricWindowResponse today,
        @Schema(description = "최근 7일 등록 변화 지표")
        AdminDashboardMetricWindowResponse last7Days,
        @Schema(description = "중복 장소 후보 그룹 수", example = "2")
        long duplicatePlaceGroupCount,
        @Schema(description = "7일 이내 임시 밴 만료 예정 사용자 수", example = "4")
        long expiringBannedUserCount,
        @Schema(description = "location 좌표 정보가 누락된 장소 수", example = "1")
        long missingLocationPlaceCount,
        @Schema(description = "밴 만료 예정 집계 종료 시각", example = "2026-07-28T15:30:00")
        LocalDateTime expiringBanUntil,
        @Schema(description = "지표 집계 기준 시각", example = "2026-07-21T15:30:00")
        LocalDateTime collectedAt
) {
}
