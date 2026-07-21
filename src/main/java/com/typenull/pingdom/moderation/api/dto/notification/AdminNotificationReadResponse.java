package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 알림 읽음 처리 응답")
public record AdminNotificationReadResponse(
        @Schema(description = "읽음 처리된 알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "읽음 여부", example = "true")
        boolean read,
        @Schema(description = "처리 메시지", example = "알림을 읽음 처리했습니다.")
        String message
) {
    public static AdminNotificationReadResponse of(Long notificationId) {
        return new AdminNotificationReadResponse(notificationId, true, "알림을 읽음 처리했습니다.");
    }
}
