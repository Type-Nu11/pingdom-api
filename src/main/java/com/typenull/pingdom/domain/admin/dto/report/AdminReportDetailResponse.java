package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 신고 상세 응답")
public record AdminReportDetailResponse(
        @Schema(description = "신고 ID", example = "1")
        Long reportId,
        @Schema(description = "신고 대상 이미지 ID", example = "10")
        Long imageId,
        @Schema(description = "신고 대상 사용자 ID", example = "5")
        Long reportedUserId,
        @Schema(description = "신고 대상 이미지 URL", example = "https://cdn.example.com/map/10.jpg")
        String imageUrl,
        @Schema(description = "신고자 사용자 ID", example = "7")
        Long reporterUserId,
        @Schema(description = "신고자 아이디", example = "reporter01")
        String reporterUsername,
        @Schema(description = "신고 사유", example = "부적절한 사진입니다.")
        String reason,
        @Schema(description = "신고 처리 상태", example = "PENDING")
        PictureReportStatus status,
        @Schema(description = "처리 시각", example = "2026-05-21T10:15:30", nullable = true)
        LocalDateTime processedAt
) {
}
