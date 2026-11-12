package com.typenull.pingdom.notification.outbox;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
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
    private ObjectMapper objectMapper;

    /**
     * 모의 메일 발송기와 이력 recorder를 연결해 재설정 페이로드 오류 처리를 검사.
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new PasswordResetOutboxHandler(emailSender, objectMapper, notificationDeliveryRecorder);
    }

    @Test
    void recordsSuccessfulPasswordResetDelivery() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 12, 15, 30);
        String payload = objectMapper.writeValueAsString(
                new PasswordResetOutboxPayload(1L, "user@example.com", "reset-token", expiresAt)
        );
        when(emailSender.sendPasswordResetEmail("user@example.com", "reset-token", expiresAt))
                .thenReturn(EmailSendResult.sent("postmark-reset-message-id"));

        handler.handle(EVENT_ID, payload);

        verify(emailSender).sendPasswordResetEmail("user@example.com", "reset-token", expiresAt);
        verify(notificationDeliveryRecorder).recordEmailSuccess(
                1L,
                EVENT_ID,
                OutboxEventType.PASSWORD_RESET_REQUESTED,
                "user@example.com",
                "postmark-reset-message-id"
        );
    }

    /**
     * 공백 페이로드는 IllegalArgumentException을 발생시키고 비밀번호 재설정 유형의 재시도 불가 페이로드 오류를 기록하는지 검증.
     */
    @Test
    void rejectsBlankPasswordResetPayload() {
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
