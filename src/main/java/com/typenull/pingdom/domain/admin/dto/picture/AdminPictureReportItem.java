package com.typenull.pingdom.domain.admin.dto.picture;

import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import java.time.LocalDateTime;

public record AdminPictureReportItem(
        Long reportId,
        Long reporterUserId,
        String reporterUsername,
        String reason,
        PictureReportStatus status,
        LocalDateTime processedAt
) {}
