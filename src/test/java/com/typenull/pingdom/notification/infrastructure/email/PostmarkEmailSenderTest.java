package com.typenull.pingdom.notification.infrastructure.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import com.typenull.pingdom.identity.application.port.EmailSendException;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostmarkEmailSenderTest {

    /**
     * 기본 재설정 URL에 쿼리가 있으면 앰퍼샌드로 이어 붙이고 이메일을 URL 인코딩한 링크를 생성하는지 검증한다.
     */
    @Test
    void appendsResetLinkQueryParameters() {
        PostmarkEmailSender sender = new PostmarkEmailSender(new PostmarkProperties(
                "test-token",
                "no-reply@example.com",
                "https://example.com/verify",
                "https://example.com/reset?mode=reset"
        ));

        String link = sender.buildPasswordResetLink("user@example.com", "reset-token");

        assertEquals(
                "https://example.com/reset?mode=reset&email=user%40example.com&token=reset-token",
                link
        );
    }

    /**
     * 모의 Postmark 발송 응답의 메시지 ID를 이메일 발송 결과에 그대로 반환하는지 검증한다.
     */
    @Test
    void returnsPostmarkVerificationMessageId() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        MessageResponse response = new MessageResponse();
        response.setMessageId("postmark-message-id");
        when(apiClient.deliverMessage(any(Message.class))).thenReturn(response);

        PostmarkEmailSender sender = new PostmarkEmailSender(properties(), apiClient);

        EmailSendResult result = sender.sendVerificationEmail("user@example.com", "123456");

        assertEquals("postmark-message-id", result.providerMessageId());
    }

    /**
     * 인증 메일의 From에 설정된 발신 주소를 사용하며 공급자 메시지 ID도 결과에 유지하는지 검증한다.
     */
    @Test
    void usesConfiguredVerificationSender() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        MessageResponse response = new MessageResponse();
        response.setMessageId("postmark-message-id");
        when(apiClient.deliverMessage(any(Message.class))).thenReturn(response);

        PostmarkEmailSender sender = new PostmarkEmailSender(new PostmarkProperties(
                "test-token",
                "support@example.com",
                "https://example.com/verify",
                "https://example.com/reset"
        ), apiClient);

        EmailSendResult result = sender.sendVerificationEmail("user@example.com", "123456");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(apiClient).deliverMessage(messageCaptor.capture());
        assertEquals("postmark-message-id", result.providerMessageId());
        assertEquals("support@example.com", messageCaptor.getValue().getFrom());
    }

    /**
     * 공백뿐인 발신 주소 설정을 조회하면 설정 필요 메시지를 가진 PostmarkConfigurationException이 발생하는지 검증한다.
     */
    @Test
    void rejectsBlankPostmarkSender() {
        PostmarkProperties properties = new PostmarkProperties(
                "test-token",
                " ",
                "https://example.com/verify",
                "https://example.com/reset"
        );

        PostmarkConfigurationException exception = assertThrows(
                PostmarkConfigurationException.class,
                properties::validatedFromEmail
        );

        assertEquals("postmark.from-email 설정이 필요합니다.", exception.getMessage());
    }

    /**
     * Postmark 422 오류를 이메일 발송 실패 코드·공급자 코드 422·재시도 불가 상태로 변환하는지 검증한다.
     */
    @Test
    void mapsPostmarkVerificationFailure() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        when(apiClient.deliverMessage(any(Message.class))).thenThrow(new PostmarkException("invalid", 422));

        PostmarkEmailSender sender = new PostmarkEmailSender(properties(), apiClient);

        EmailSendException exception = assertThrows(
                EmailSendException.class,
                () -> sender.sendVerificationEmail("user@example.com", "123456")
        );

        assertEquals("POSTMARK_SEND_FAILED", exception.getErrorCode());
        assertEquals("422", exception.getProviderErrorCode());
        assertFalse(exception.isRetryable());
    }

    /**
     * 외부 호출 없이 발송 매핑을 확인할 테스트 토큰·발신자·인증 및 재설정 URL 설정을 제공한다.
     */
    private PostmarkProperties properties() {
        return new PostmarkProperties(
                "test-token",
                "no-reply@example.com",
                "https://example.com/verify",
                "https://example.com/reset"
        );
    }
}
