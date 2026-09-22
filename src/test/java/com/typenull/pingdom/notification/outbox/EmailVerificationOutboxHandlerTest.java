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

    /**
     * JSON 페이로드를 실제 역직렬화하면서 메일 발송과 이력 기록은 대체할 인증 메일 핸들러를 구성한다.
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new EmailVerificationOutboxHandler(emailSender, objectMapper, notificationDeliveryRecorder);
    }

    /**
     * 인증 메일 발송 성공 시 사용자·이벤트·수신자·공급자 메시지 ID를 인증 요청 유형의 성공 이력으로 전달하는지 검증한다.
     */
    @Test
    void recordsSuccessfulVerificationDelivery() throws Exception {
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

    /**
     * 메일 발송 오류의 공급자 코드·내부 코드·사유·재시도 여부를 실패 이력에 전달하고 EmailSendException을 호출자에게 전파하는지 검증한다.
     */
    @Test
    void recordsAndRethrowsVerificationFailure() throws Exception {
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

    /**
     * null 페이로드는 IllegalArgumentException을 발생시키고 수신자 없이 재시도 불가 페이로드 오류 이력을 기록하는지 검증한다.
     */
    @Test
    void rejectsNullVerificationPayload() {
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
