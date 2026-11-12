package com.typenull.pingdom.notification.outbox;

import java.time.LocalDateTime;

public record EmailVerificationOutboxPayload(
        Long userId,
        String recipientEmail,
        String verificationCode,
        LocalDateTime expiresAt
) {
    public EmailVerificationOutboxPayload(Long userId, String recipientEmail, String verificationCode) {
        this(userId, recipientEmail, verificationCode, null);
    }

    public EmailVerificationOutboxPayload(String recipientEmail, String verificationCode) {
        this(null, recipientEmail, verificationCode, null);
    }
}
