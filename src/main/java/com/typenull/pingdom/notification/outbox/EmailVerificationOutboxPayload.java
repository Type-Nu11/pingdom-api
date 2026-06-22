package com.typenull.pingdom.notification.outbox;

public record EmailVerificationOutboxPayload(
        String recipientEmail,
        String verificationCode
) {
}
