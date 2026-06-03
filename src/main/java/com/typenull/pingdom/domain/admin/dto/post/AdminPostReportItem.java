package com.typenull.pingdom.domain.admin.dto.post;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import java.time.LocalDateTime;

public record AdminPostReportItem(
        Long reportId,
        Long reporterUserId,
        String reporterUsername,
        String reason,
        PostReportStatus status,
        LocalDateTime processedAt
) {}
