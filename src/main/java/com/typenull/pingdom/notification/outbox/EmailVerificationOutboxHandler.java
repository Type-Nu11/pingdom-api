package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSendException;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationOutboxHandler implements OutboxEventHandler {

    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryRecorder notificationDeliveryRecorder;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.EMAIL_VERIFICATION_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        EmailVerificationOutboxPayload event = deserialize(eventId, payload);
        try {
            EmailSendResult result = emailSender.sendVerificationEmail(
                    event.recipientEmail(),
                    event.verificationCode()
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
                    failureReason(exception),
                    retryable(exception)
            );
            throw exception;
        }
    }

    private EmailVerificationOutboxPayload deserialize(String eventId, String payload) {
        try {
            return objectMapper.readValue(payload, EmailVerificationOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            notificationDeliveryRecorder.recordEmailFailure(
                    null,
                    eventId,
                    supportedType(),
                    null,
                    null,
                    NotificationDeliveryRecorder.ERROR_EMAIL_PAYLOAD_INVALID,
                    exception.getOriginalMessage(),
                    false
            );
            throw new IllegalArgumentException("이메일 Outbox payload 역직렬화에 실패했습니다.", exception);
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

    private String failureReason(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private boolean retryable(RuntimeException exception) {
        if (exception instanceof EmailSendException emailSendException) {
            return emailSendException.isRetryable();
        }
        return true;
    }
}
