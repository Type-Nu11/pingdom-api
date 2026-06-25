package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 알림 발송 결과 목록 응답")
public record AdminNotificationDeliveryResponse(
        List<AdminNotificationDeliveryItem> deliveries,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminNotificationDeliveryResponse of(
            List<AdminNotificationDeliveryItem> deliveries,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminNotificationDeliveryResponse(deliveries, page, limit, totalCount, totalPages, page < totalPages);
    }
}
