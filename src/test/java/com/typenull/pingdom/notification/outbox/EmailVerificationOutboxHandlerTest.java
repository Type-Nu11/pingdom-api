package com.typenull.pingdom.notification.outbox;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSendException;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationOutboxHandlerTest {

    private static final String EVENT_ID = "event-id";

    @Mock
    private EmailSender emailSender;

    @Mock
    private NotificationDeliveryRecorder notificationDeliveryRecorder;

    private EmailVerificationOutboxHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new EmailVerificationOutboxHandler(emailSender, objectMapper, notificationDeliveryRecorder);
    }

    @Test
    void handleRecordsSuccessfulEmailDelivery() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new EmailVerificationOutboxPayload(1L, "user@example.com", "123456")
        );
        when(emailSender.sendVerificationEmail("user@example.com", "123456"))
                .thenReturn(EmailSendResult.sent("postmark-message-id"));

        handler.handle(EVENT_ID, payload);

        verify(notificationDeliveryRecorder).recordEmailSuccess(
                1L,
                EVENT_ID,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "user@example.com",
                "postmark-message-id"
        );
    }

    @Test
    void handleRecordsFailedEmailDeliveryAndRethrows() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new EmailVerificationOutboxPayload(1L, "user@example.com", "123456")
        );
        EmailSendException failure = new EmailSendException(
                "failed",
                "POSTMARK_SEND_FAILED",
                "500",
                true,
                null
        );
        when(emailSender.sendVerificationEmail("user@example.com", "123456")).thenThrow(failure);

        assertThrows(EmailSendException.class, () -> handler.handle(EVENT_ID, payload));

        verify(notificationDeliveryRecorder).recordEmailFailure(
                1L,
                EVENT_ID,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "user@example.com",
                "500",
                "POSTMARK_SEND_FAILED",
                "failed",
                true
        );
    }

    @Test
    void handleRecordsInvalidPayloadWhenPayloadIsNull() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(EVENT_ID, null));

        verify(notificationDeliveryRecorder).recordEmailFailure(
                null,
                EVENT_ID,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                null,
                null,
                NotificationDeliveryRecorder.ERROR_EMAIL_PAYLOAD_INVALID,
                "Payload is empty",
                false
        );
    }
}
