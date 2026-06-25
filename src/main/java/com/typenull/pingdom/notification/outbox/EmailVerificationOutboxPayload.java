package com.typenull.pingdom.notification.outbox;

public record EmailVerificationOutboxPayload(
        Long userId,
        String recipientEmail,
        String verificationCode
) {
    public EmailVerificationOutboxPayload(String recipientEmail, String verificationCode) {
        this(null, recipientEmail, verificationCode);
    }
}
