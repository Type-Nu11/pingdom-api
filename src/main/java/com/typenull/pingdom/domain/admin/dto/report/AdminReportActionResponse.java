package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import java.time.LocalDateTime;

public record AdminReportActionResponse(
        Long reportId,
        PictureReportStatus status,
        Long reportedUserId,
        boolean banned,
        LocalDateTime processedAt
) {
}
