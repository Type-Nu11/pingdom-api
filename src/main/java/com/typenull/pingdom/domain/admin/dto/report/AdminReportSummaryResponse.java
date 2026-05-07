package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import java.time.LocalDateTime;

public record AdminReportSummaryResponse(
        Long reportId,
        Long imageId,
        Long reportedUserId,
        Long reporterUserId,
        String reporterUsername,
        String reason,
        PictureReportStatus status,
        LocalDateTime processedAt
) {
}
