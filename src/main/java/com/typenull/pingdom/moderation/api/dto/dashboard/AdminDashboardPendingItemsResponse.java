package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 처리 필요 항목 목록")
public record AdminDashboardPendingItemsResponse(
        @Schema(description = "처리 필요 항목")
        List<AdminDashboardPendingItem> items,
        @Schema(description = "필터링 전 전체 PENDING 처리 필요 항목 수", example = "8")
        long totalCount
) {
}
