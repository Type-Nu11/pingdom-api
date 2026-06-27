package com.typenull.pingdom.notification.outbox;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetOutboxHandlerTest {

    private static final String EVENT_ID = "event-id";

    @Mock
    private EmailSender emailSender;

    @Mock
    private NotificationDeliveryRecorder notificationDeliveryRecorder;

    private PasswordResetOutboxHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PasswordResetOutboxHandler(emailSender, new ObjectMapper(), notificationDeliveryRecorder);
    }

    @Test
    void handleRecordsInvalidPayloadWhenPayloadIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(EVENT_ID, " "));

        verify(notificationDeliveryRecorder).recordEmailFailure(
                null,
                EVENT_ID,
                OutboxEventType.PASSWORD_RESET_REQUESTED,
                null,
                null,
                NotificationDeliveryRecorder.ERROR_EMAIL_PAYLOAD_INVALID,
                "Payload is empty",
                false
        );
    }
}
