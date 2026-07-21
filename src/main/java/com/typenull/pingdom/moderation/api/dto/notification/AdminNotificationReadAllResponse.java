package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 전체 알림 읽음 처리 응답")
public record AdminNotificationReadAllResponse(
        @Schema(description = "읽음 처리된 알림 개수", example = "3")
        long updatedCount,
        @Schema(description = "처리 메시지", example = "전체 알림을 읽음 처리했습니다.")
        String message
) {
    public static AdminNotificationReadAllResponse of(long updatedCount) {
        return new AdminNotificationReadAllResponse(updatedCount, "전체 알림을 읽음 처리했습니다.");
    }
}
