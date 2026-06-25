package com.typenull.pingdom.notification.outbox;

import java.time.LocalDateTime;

public record PasswordResetOutboxPayload(
        Long userId,
        String recipientEmail,
        String resetToken,
        LocalDateTime expiresAt
) {
    public PasswordResetOutboxPayload(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        this(null, recipientEmail, resetToken, expiresAt);
    }
}
