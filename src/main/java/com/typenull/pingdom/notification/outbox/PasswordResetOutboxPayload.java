package com.typenull.pingdom.notification.outbox;

import java.time.LocalDateTime;

public record PasswordResetOutboxPayload(
        String recipientEmail,
        String resetToken,
        LocalDateTime expiresAt
) {
}
