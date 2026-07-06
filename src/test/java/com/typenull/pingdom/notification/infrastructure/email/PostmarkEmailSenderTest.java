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

    @Test
    void passwordResetLinkAppendsParametersWithAmpersandWhenBaseUrlAlreadyHasQuery() {
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

    @Test
    void sendVerificationEmailReturnsPostmarkMessageId() throws Exception {
        ApiClient apiClient = mock(ApiClient.class);
        MessageResponse response = new MessageResponse();
        response.setMessageId("postmark-message-id");
        when(apiClient.deliverMessage(any(Message.class))).thenReturn(response);

        PostmarkEmailSender sender = new PostmarkEmailSender(properties(), apiClient);

        EmailSendResult result = sender.sendVerificationEmail("user@example.com", "123456");

        assertEquals("postmark-message-id", result.providerMessageId());
    }

    @Test
    void sendVerificationEmailUsesConfiguredFromEmail() throws Exception {
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

    @Test
    void postmarkPropertiesRejectsMissingFromEmailAtConfigurationBoundary() {
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

    @Test
    void sendVerificationEmailMapsPostmarkErrorCode() throws Exception {
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

    private PostmarkProperties properties() {
        return new PostmarkProperties(
                "test-token",
                "no-reply@example.com",
                "https://example.com/verify",
                "https://example.com/reset"
        );
    }
}
