package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PostReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 신고 처리 응답")
public record AdminReportActionResponse(
        @Schema(description = "신고 ID", example = "1")
        Long reportId,
        @Schema(description = "변경된 신고 처리 상태", example = "ACCEPTED")
        PostReportStatus status,
        @Schema(description = "신고 대상 사용자 ID", example = "5")
        Long reportedUserId,
        @Schema(description = "밴 처리 여부", example = "true")
        boolean banned,
        @Schema(description = "처리 시각", example = "2026-05-21T10:15:30")
        LocalDateTime processedAt
) {
}
