package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 처리 필요 항목")
public record AdminDashboardPendingItem(
        @Schema(description = "처리 대상 유형", example = "POST_REPORT", allowableValues = {"POST_REPORT", "MERCHANT_PLACE_APPLICATION"})
        AdminDashboardPendingItemType type,
        @Schema(description = "처리 항목 ID. POST_REPORT에서는 신고 ID와 동일", example = "30")
        Long targetId,
        @Schema(description = "신고 ID. 신고 처리 화면으로 이동할 때 사용", example = "30")
        Long reportId,
        @Schema(description = "신고 대상 게시글 ID. 게시글 상세 화면으로 이동할 때 사용", example = "22", nullable = true)
        Long postId,
        @Schema(description = "처리 대상 제목", example = "야경이 좋은 장소")
        String title,
        @Schema(description = "처리 대상 상태", example = "PENDING")
        String status,
        @Schema(description = "생성일", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt,
        @Schema(description = "관리자 화면 이동 경로. 통합 신청 항목에서만 제공", example = "/admin/merchant-place-applications/12", nullable = true)
        String navigationPath
) {
}
