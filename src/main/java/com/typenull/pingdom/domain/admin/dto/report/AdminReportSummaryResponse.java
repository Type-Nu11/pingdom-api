package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 신고 목록 요약 응답")
public record AdminReportSummaryResponse(
        @Schema(description = "신고 ID", example = "1")
        Long reportId,
        @Schema(description = "신고 대상 이미지 ID", example = "10")
        Long imageId,
        @Schema(description = "신고자 아이디", example = "reporter01")
        String reporterUsername,
        @Schema(description = "신고 사유", example = "부적절한 사진입니다.")
        String reason,
        @Schema(description = "신고 처리 상태", example = "PENDING")
        PictureReportStatus status
) {
}
