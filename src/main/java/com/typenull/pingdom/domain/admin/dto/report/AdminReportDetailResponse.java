package com.typenull.pingdom.domain.admin.dto.report;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import java.time.LocalDateTime;

public record AdminReportDetailResponse(
        Long reportId,
        Long imageId,
        Long reportedUserId,
        String imageUrl,
        Long reporterUserId,
        String reporterUsername,
        String reason,
        PictureReportStatus status,
        LocalDateTime processedAt
) {
}
