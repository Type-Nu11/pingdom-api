package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;

final class OutboxEventContractFixture {

    static final String AGGREGATE_TYPE = "USER";
    static final String AGGREGATE_ID = "100";
    static final String RECIPIENT_EMAIL = "outbox-contract@pingdom.test";
    static final String VERIFICATION_CODE = "123456";

    private OutboxEventContractFixture() {
    }

    static String deduplicationKey(String suffix) {
        return "EMAIL_VERIFICATION:" + AGGREGATE_ID + ":" + suffix;
    }

    static EmailVerificationOutboxPayload emailVerificationPayload() {
        return new EmailVerificationOutboxPayload(Long.valueOf(AGGREGATE_ID), RECIPIENT_EMAIL, VERIFICATION_CODE);
    }
}
