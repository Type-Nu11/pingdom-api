package com.typenull.pingdom.moderation.api.dto.notification;

import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 알림 발송 결과 아이템")
public record AdminNotificationDeliveryItem(
        @Schema(description = "발송 결과 ID", example = "1")
        Long deliveryId,
        @Schema(description = "발송 채널", example = "FCM")
        NotificationDeliveryChannel channel,
        @Schema(description = "발송 상태", example = "SUCCEEDED")
        NotificationDeliveryStatus status,
        @Schema(description = "수신 사용자 ID", example = "10")
        Long userId,
        @Schema(description = "인앱 알림 ID", example = "100")
        Long notificationId,
        @Schema(description = "인앱 알림 유형", example = "NEW_LIKE")
        String notificationType,
        @Schema(description = "Outbox 이벤트 ID")
        String outboxEventId,
        @Schema(description = "Outbox 이벤트 유형", example = "MAP_IMAGE_LIKED")
        String outboxEventType,
        @Schema(description = "외부 발송 provider message id")
        String providerMessageId,
        @Schema(description = "외부 발송 provider error code")
        String providerErrorCode,
        @Schema(description = "내부 실패 분류 코드")
        String errorCode,
        @Schema(description = "실패 사유")
        String failureReason,
        @Schema(description = "재시도 가능 여부", example = "true")
        boolean retryable,
        @Schema(description = "발송 시도 횟수", example = "1")
        int attemptCount,
        @Schema(description = "생성 시각", example = "2026-06-25T10:15:30")
        LocalDateTime createdAt,
        @Schema(description = "수정 시각", example = "2026-06-25T10:15:30")
        LocalDateTime updatedAt
) {
}
