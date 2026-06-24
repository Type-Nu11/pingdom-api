package com.typenull.pingdom.moderation.api.dto.report;

public record ReportedUsersItem(
        Long ReportId,
        Long reporterUserId,
        String reporterUsername,
        Long reportedImageId,
        Long reportedUserId,
        String reason
) {
}
