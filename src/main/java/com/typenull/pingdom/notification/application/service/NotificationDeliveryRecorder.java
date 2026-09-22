package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FCM·이메일 발송 결과를 공통 기록 요청으로 바꾸고 기록 실패 시에도 본 발송 흐름 유지.
 * 저장 예외를 경고로 남기고 삼키므로 결과 기록과 이를 이용한 중복 전송 억제는 항상 성공한다고 보장할 수 없음.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryRecorder {

    public static final String ERROR_FCM_INVALID_TOKEN = "FCM_INVALID_TOKEN";
    public static final String ERROR_FCM_SEND_FAILED = "FCM_SEND_FAILED";
    public static final String ERROR_EMAIL_SEND_FAILED = "EMAIL_SEND_FAILED";
    public static final String ERROR_EMAIL_PAYLOAD_INVALID = "EMAIL_PAYLOAD_INVALID";

    private final NotificationDeliveryRecordWriter writer;

    /**
     * FCM 공급자 메시지 ID와 알림·Outbox 정보를 성공 기록 요청으로 변환.
     * Writer가 원본 토큰을 해시하며 저장 실패는 흡수하므로 이 호출의 정상 반환만으로 기록 저장을 확정할 수 없음.
     */
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

    /**
     * 토큰별 실패 정보를 모아 재시도 가능하면 RETRY_SCHEDULED, 불가능하면 FAILED로 기록을 요청.
     * 최대 시도 수에 따른 최종 상태는 Writer가 결정하며 저장 실패는 본 발송 흐름으로의 전파 대상에서 제외.
     */
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
                retryable ? NotificationDeliveryStatus.RETRY_SCHEDULED : NotificationDeliveryStatus.FAILED,
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

    public boolean isFcmDeliverySucceeded(String outboxEventId, String token) {
        return writer.isFcmDeliverySucceeded(outboxEventId, token);
    }

    public Long findFcmNotificationId(String outboxEventId) {
        return writer.findFcmNotificationId(outboxEventId);
    }

    /**
     * 이메일 공급자 메시지 ID와 수신자·Outbox 정보를 성공 기록 요청으로 변환.
     * 앱 알림 ID는 없으며 저장 오류는 흡수. 이메일 발송 자체의 실행과 재시도는 처리 범위 외.
     */
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

    /**
     * 이메일 실패 기록의 요청 상태는 retryable 값과 별개로 RETRY_SCHEDULED를 사용.
     * retryable은 별도 진단 값으로 보관되며 최대 시도 도달 여부는 Writer에서 다시 판정.
     */
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
