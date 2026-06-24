package com.typenull.pingdom.moderation.api.dto.appeal;

import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import java.time.LocalDateTime;

public record AdminReportAppealActionResponse(
        Long appealId,
        Long reportId,
        Long postId,
        ReportAppealStatus status,
        LocalDateTime processedAt
) {
}
