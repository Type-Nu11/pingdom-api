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

    /**
     * 모의 메일 발송기와 이력 recorder를 연결해 재설정 페이로드 오류 처리를 검사한다.
     */
    @BeforeEach
    void setUp() {
        handler = new PasswordResetOutboxHandler(emailSender, new ObjectMapper(), notificationDeliveryRecorder);
    }

    /**
     * 공백 페이로드는 IllegalArgumentException을 발생시키고 비밀번호 재설정 유형의 재시도 불가 페이로드 오류를 기록하는지 검증한다.
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
