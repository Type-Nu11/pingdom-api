package com.typenull.pingdom.moderation.api.dto.notification;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 알림 아이템")
public record AdminNotificationItem(
        @Schema(description = "알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "수신 사용자 ID", example = "10")
        Long userId,
        @Schema(description = "알림 유형", example = "NEW_LIKE")
        NotificationType type,
        @Schema(description = "알림 제목", example = "좋아요 알림")
        String title,
        @Schema(description = "알림 내용", example = "pingdom님이 좋아요를 눌렀어요")
        String body,
        @Schema(description = "연결 대상 ID")
        String token,
        @Schema(description = "읽음 여부", example = "false")
        boolean read,
        @Schema(description = "생성 시각", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt
) {
    public static AdminNotificationItem from(Notifications notification) {
        return new AdminNotificationItem(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getToken(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
