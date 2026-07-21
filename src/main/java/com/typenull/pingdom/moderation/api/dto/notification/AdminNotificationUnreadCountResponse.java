package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 미확인 알림 개수 응답")
public record AdminNotificationUnreadCountResponse(
        @Schema(description = "미확인 알림 개수", example = "3")
        long unreadCount
) {
}
