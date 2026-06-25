package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;

public record NotificationDeliveryRecordRequest(
        NotificationDeliveryChannel channel,
        NotificationDeliveryStatus status,
        Long userId,
        Long notificationId,
        String notificationType,
        String outboxEventId,
        String outboxEventType,
        String recipient,
        String providerMessageId,
        String providerErrorCode,
        String errorCode,
        String failureReason,
        boolean retryable
) {
}
