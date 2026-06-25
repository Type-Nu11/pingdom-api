package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryRecorder {

    public static final String ERROR_FCM_INVALID_TOKEN = "FCM_INVALID_TOKEN";
    public static final String ERROR_FCM_SEND_FAILED = "FCM_SEND_FAILED";
    public static final String ERROR_EMAIL_SEND_FAILED = "EMAIL_SEND_FAILED";
    public static final String ERROR_EMAIL_PAYLOAD_INVALID = "EMAIL_PAYLOAD_INVALID";

    private final NotificationDeliveryRecordWriter writer;

    public void recordFcmSuccess(
            Long userId,
            Long notificationId,
            NotificationType notificationType,
            String outboxEventId,
            String token,
            String providerMessageId
    ) {
        recordSafely(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.FCM,
                NotificationDeliveryStatus.SUCCEEDED,
                userId,
                notificationId,
                notificationType == null ? null : notificationType.name(),
                outboxEventId,
                fcmOutboxEventType(notificationType, outboxEventId),
                token,
                providerMessageId,
                null,
                null,
                null,
                false
        ));
    }

    public void recordFcmFailure(
            Long userId,
            Long notificationId,
            NotificationType notificationType,
            String outboxEventId,
            String token,
            String providerErrorCode,
            String errorCode,
            String failureReason,
            boolean retryable
    ) {
        recordSafely(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.FCM,
                NotificationDeliveryStatus.FAILED,
                userId,
                notificationId,
                notificationType == null ? null : notificationType.name(),
                outboxEventId,
                fcmOutboxEventType(notificationType, outboxEventId),
                token,
                null,
                providerErrorCode,
                errorCode,
                failureReason,
                retryable
        ));
    }

    public void recordEmailSuccess(
            Long userId,
            String outboxEventId,
            OutboxEventType outboxEventType,
            String recipientEmail,
            String providerMessageId
    ) {
        recordSafely(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.EMAIL,
                NotificationDeliveryStatus.SUCCEEDED,
                userId,
                null,
                null,
                outboxEventId,
                outboxEventType == null ? null : outboxEventType.name(),
                recipientEmail,
                providerMessageId,
                null,
                null,
                null,
                false
        ));
    }

    public void recordEmailFailure(
            Long userId,
            String outboxEventId,
            OutboxEventType outboxEventType,
            String recipientEmail,
            String providerErrorCode,
            String errorCode,
            String failureReason,
            boolean retryable
    ) {
        recordSafely(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.EMAIL,
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                userId,
                null,
                null,
                outboxEventId,
                outboxEventType == null ? null : outboxEventType.name(),
                recipientEmail,
                null,
                providerErrorCode,
                errorCode,
                failureReason,
                retryable
        ));
    }

    private void recordSafely(NotificationDeliveryRecordRequest request) {
        try {
            writer.record(request);
        } catch (RuntimeException exception) {
            log.warn(
                    "알림 발송 결과 기록에 실패했습니다. channel={}, outboxEventId={}, reason={}",
                    request.channel(),
                    request.outboxEventId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private String fcmOutboxEventType(NotificationType notificationType, String outboxEventId) {
        if (outboxEventId == null || outboxEventId.isBlank()) {
            return null;
        }
        if (notificationType == NotificationType.NEW_LIKE) {
            return OutboxEventType.MAP_IMAGE_LIKED.name();
        }
        return null;
    }
}
