package com.typenull.pingdom.moderation.api.dto.appeal;

import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import java.time.LocalDateTime;

public record AdminReportAppealItem(
        Long appealId,
        Long reportId,
        Long postId,
        Long appellantUserId,
        String appellantUsername,
        Long targetUserId,
        String reason,
        ReportAppealStatus status,
        Long adminUserId,
        String adminReason,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
}
