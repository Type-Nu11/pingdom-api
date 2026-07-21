package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 알림 목록 응답")
public record AdminNotificationResponse(
        List<AdminNotificationItem> notifications,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminNotificationResponse of(
            List<AdminNotificationItem> notifications,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminNotificationResponse(notifications, page, limit, totalCount, totalPages, page < totalPages);
    }
}
