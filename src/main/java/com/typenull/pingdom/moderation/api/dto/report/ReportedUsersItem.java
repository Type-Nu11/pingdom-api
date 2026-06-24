package com.typenull.pingdom.moderation.api.dto.report;

public record ReportedUsersItem(
        Long reportId,
        Long reporterUserId,
        String reporterUsername,
        Long reportedImageId,
        Long reportedUserId,
        String reason
) {
}
