package com.typenull.pingdom.notification.infrastructure.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
