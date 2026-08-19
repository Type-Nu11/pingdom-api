package com.typenull.pingdom.moderation.api.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "처리 필요 항목 유형")
public enum AdminDashboardPendingItemType {
    @Schema(description = "게시글 신고: targetId와 reportId는 신고 ID, postId는 신고 대상 게시글 ID")
    POST_REPORT,
    @Schema(description = "통합 사업자·장소 신청: targetId는 신청 ID이며 navigationPath로 심사 상세로 이동")
    MERCHANT_PLACE_APPLICATION
}
