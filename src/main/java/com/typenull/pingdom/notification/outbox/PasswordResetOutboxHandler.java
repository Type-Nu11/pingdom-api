package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSendException;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.application.service.auth.AuthEmailDispatchPolicy;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 유효한 현재 재설정 요청만 발송하고 저장된 성공/미발송 종료 이력을 재사용한다.
 * 결과 기록 전 프로세스 종료·응답 불명 구간의 외부 중복 전달 가능성은 남는다.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetOutboxHandler implements OutboxEventHandler {

    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryRecorder notificationDeliveryRecorder;
    private final AuthEmailDispatchPolicy dispatchPolicy;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.PASSWORD_RESET_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        PasswordResetOutboxPayload event = deserialize(eventId, payload);
        try {
            if (notificationDeliveryRecorder.isEmailDeliveryTerminal(eventId, event.recipientEmail())) {
                return;
            }
            if (!dispatchPolicy.canSendPasswordReset(event.userId(), event.recipientEmail(), event.resetToken(), event.expiresAt())) {
                notificationDeliveryRecorder.recordEmailStale(event.userId(), eventId, supportedType(), event.recipientEmail());
                return;
            }
        } catch (RuntimeException exception) {
            // 조회/미발송 종료 기록 장애는 발송 없이 재시도한다. 원문 예외는 PII를 포함할 수 있다.
            throw new IllegalStateException("비밀번호 재설정 메일 발송 준비에 실패했습니다.");
        }
        try {
            EmailSendResult result = emailSender.sendPasswordResetEmail(
                    event.recipientEmail(),
                    event.resetToken(),
                    event.expiresAt()
            );
            notificationDeliveryRecorder.recordEmailSuccess(
                    event.userId(),
                    eventId,
                    supportedType(),
                    event.recipientEmail(),
                    result.providerMessageId()
            );
        } catch (RuntimeException exception) {
            notificationDeliveryRecorder.recordEmailFailure(
                    event.userId(),
                    eventId,
                    supportedType(),
                    event.recipientEmail(),
                    providerErrorCode(exception),
                    errorCode(exception),
                    "비밀번호 재설정 메일 전송에 실패했습니다.",
                    retryable(exception)
            );
            throw new EmailSendException("비밀번호 재설정 메일 전송에 실패했습니다.",
                    errorCode(exception), providerErrorCode(exception), retryable(exception), null);
        }
    }

    private PasswordResetOutboxPayload deserialize(String eventId, String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Payload is empty");
            }
            PasswordResetOutboxPayload event = objectMapper.readValue(payload, PasswordResetOutboxPayload.class);
            if (event == null) {
                throw new IllegalArgumentException("Payload is invalid");
            }
            return event;
        } catch (Exception exception) {
            notificationDeliveryRecorder.recordEmailFailure(
                    null,
                    eventId,
                    supportedType(),
                    null,
                    null,
                    NotificationDeliveryRecorder.ERROR_EMAIL_PAYLOAD_INVALID,
                    payload == null || payload.isBlank() ? "Payload is empty" : "Payload is invalid",
                    false
            );
            throw new IllegalArgumentException("비밀번호 재설정 Outbox payload 역직렬화에 실패했습니다.");
        }
    }

    private String providerErrorCode(RuntimeException exception) {
        if (exception instanceof EmailSendException emailSendException) {
            return emailSendException.getProviderErrorCode();
        }
        return null;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof EmailSendException emailSendException) {
            return emailSendException.getErrorCode();
        }
        return NotificationDeliveryRecorder.ERROR_EMAIL_SEND_FAILED;
    }

    private boolean retryable(RuntimeException exception) {
        if (exception instanceof EmailSendException emailSendException) {
            return emailSendException.isRetryable();
        }
        return true;
    }
}
