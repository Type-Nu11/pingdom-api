package com.typenull.pingdom.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSendException;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.application.service.auth.AuthEmailDispatchPolicy;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationOutboxHandlerTest {

    private static final String EVENT_ID = "event-id";
    private static final String EMAIL = "user@example.com";
    private static final String SECRET = "123456";
    private static final LocalDateTime EXPIRY = LocalDateTime.of(2026, 11, 12, 15, 30);

    @Mock private EmailSender emailSender;
    @Mock private NotificationDeliveryRecorder recorder;
    @Mock private AuthEmailDispatchPolicy dispatchPolicy;

    private EmailVerificationOutboxHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new EmailVerificationOutboxHandler(emailSender, objectMapper, recorder, dispatchPolicy);
    }

    @Test
    void sendsCurrentRequestOnceWhenCompletedDeliveryIsReplayed() throws Exception {
        String payload = payload();
        when(recorder.isEmailDeliveryTerminal(EVENT_ID, EMAIL)).thenReturn(false, true);
        when(dispatchPolicy.canSendVerification(1L, EMAIL, SECRET, EXPIRY)).thenReturn(true);
        when(emailSender.sendVerificationEmail(EMAIL, SECRET)).thenReturn(EmailSendResult.sent("message-id"));

        handler.handle(EVENT_ID, payload);
        // Outbox 완료 저장 실패 등으로 동일 이벤트를 다시 처리해도 성공 이력을 우선한다.
        handler.handle(EVENT_ID, payload);

        verify(emailSender).sendVerificationEmail(EMAIL, SECRET);
        verify(dispatchPolicy).canSendVerification(1L, EMAIL, SECRET, EXPIRY);
        verify(recorder).recordEmailSuccess(1L, EVENT_ID, OutboxEventType.EMAIL_VERIFICATION_REQUESTED, EMAIL, "message-id");
    }

    @Test
    void terminatesStaleRequestWithoutSending() throws Exception {
        handler.handle(EVENT_ID, payload());

        verifyNoInteractions(emailSender);
        verify(recorder).recordEmailStale(1L, EVENT_ID, OutboxEventType.EMAIL_VERIFICATION_REQUESTED, EMAIL);
    }

    @Test
    void retriesStaleRecordingFailureWithoutLeakingCauseOrSending() throws Exception {
        String payload = payload();
        doThrow(new IllegalStateException(EMAIL + SECRET)).when(recorder)
                .recordEmailStale(1L, EVENT_ID, OutboxEventType.EMAIL_VERIFICATION_REQUESTED, EMAIL);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> handler.handle(EVENT_ID, payload));

        assertThat(failure).hasNoCause();
        assertThat(failure.getMessage()).doesNotContain(EMAIL, SECRET);
        verifyNoInteractions(emailSender);
    }

    @Test
    void retriesDeliveryLookupFailureWithoutSendingOrDisclosingCause() throws Exception {
        String payload = payload();
        when(recorder.isEmailDeliveryTerminal(EVENT_ID, EMAIL))
                .thenThrow(new IllegalStateException(EMAIL + SECRET));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> handler.handle(EVENT_ID, payload));

        assertThat(failure).hasNoCause();
        assertThat(failure.getMessage()).doesNotContain(EMAIL, SECRET);
        verifyNoInteractions(emailSender, dispatchPolicy);
    }

    @Test
    void retriesPolicyLookupFailureInsteadOfRecordingStale() throws Exception {
        String payload = payload();
        when(dispatchPolicy.canSendVerification(1L, EMAIL, SECRET, EXPIRY))
                .thenThrow(new IllegalStateException(EMAIL + SECRET));

        assertThrows(IllegalStateException.class, () -> handler.handle(EVENT_ID, payload));

        verifyNoInteractions(emailSender);
        verify(recorder, never()).recordEmailStale(any(), anyString(), any(), anyString());
    }

    @Test
    void recordsSafeFailureAndRemovesProviderExceptionSecrets() throws Exception {
        String payload = payload();
        when(dispatchPolicy.canSendVerification(1L, EMAIL, SECRET, EXPIRY)).thenReturn(true);
        when(emailSender.sendVerificationEmail(EMAIL, SECRET)).thenThrow(new EmailSendException(
                EMAIL + SECRET, "POSTMARK_SEND_FAILED", "500", true,
                new IllegalArgumentException(SECRET)));

        EmailSendException failure = assertThrows(EmailSendException.class,
                () -> handler.handle(EVENT_ID, payload));

        assertThat(failure).hasNoCause();
        assertThat(failure.getMessage()).doesNotContain(EMAIL, SECRET);
        assertThat(failure.isRetryable()).isTrue();
        verify(recorder).recordEmailFailure(1L, EVENT_ID, OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                EMAIL, "500", "POSTMARK_SEND_FAILED", "인증 메일 전송에 실패했습니다.", true);
    }

    @Test
    void removesPayloadSecretsFromParsingFailure() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(EVENT_ID, "{\"recipientEmail\":\"" + EMAIL + "\",\"verificationCode\":\"" + SECRET + "\""));

        assertThat(failure).hasNoCause();
        assertThat(failure.getMessage()).doesNotContain(EMAIL, SECRET);
        verifyNoInteractions(emailSender, dispatchPolicy);
    }

    @Test
    void rejectsBlankPayload() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(EVENT_ID, " "));

        verify(recorder).recordEmailFailure(null, EVENT_ID, OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                null, null, NotificationDeliveryRecorder.ERROR_EMAIL_PAYLOAD_INVALID,
                "Payload is empty", false);
    }

    private String payload() throws Exception {
        return objectMapper.writeValueAsString(new EmailVerificationOutboxPayload(1L, EMAIL, SECRET, EXPIRY));
    }
}
