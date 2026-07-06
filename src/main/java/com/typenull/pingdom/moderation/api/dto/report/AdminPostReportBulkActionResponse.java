package com.typenull.pingdom.moderation.api.dto.report;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 게시글 단위 신고 일괄 처리 응답")
public record AdminPostReportBulkActionResponse(
        @Schema(description = "게시글 ID", example = "10")
        Long postId,
        @Schema(description = "일괄 처리된 신고 상태", example = "ACCEPTED")
        PostReportStatus status,
        @Schema(description = "처리된 신고 수", example = "3")
        int processedReportCount,
        @Schema(description = "게시글 공개 상태", example = "AUTO_HIDDEN")
        MapImageVisibilityStatus visibilityStatus,
        @Schema(description = "게시글 숨김 처리 시각", example = "2026-05-21T10:15:30")
        LocalDateTime hiddenAt,
        @Schema(description = "게시글 숨김 사유", example = "REPORT_BULK_ACCEPTED")
        String hiddenReason,
        @Schema(description = "신고 처리 시각", example = "2026-05-21T10:15:30")
        LocalDateTime processedAt
) {
}
