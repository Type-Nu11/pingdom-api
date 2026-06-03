package com.typenull.pingdom.moderation.api.dto.post;

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
