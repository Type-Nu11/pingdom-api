package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 최근 운영 활동")
public record AdminDashboardRecentActivitiesResponse(
        @Schema(description = "최근 장소 등록 내역")
        List<AdminDashboardRecentPlaceItem> places,
        @Schema(description = "최근 게시글 등록 내역")
        List<AdminDashboardRecentPostItem> posts,
        @Schema(description = "최근 신고 처리 내역")
        List<AdminDashboardRecentReportItem> reports,
        @Schema(description = "최근 사용자 밴 및 밴 해제 내역")
        List<AdminDashboardRecentUserSanctionItem> userSanctions
) {
}
