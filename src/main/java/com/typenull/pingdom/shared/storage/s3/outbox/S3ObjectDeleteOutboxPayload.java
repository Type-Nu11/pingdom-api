package com.typenull.pingdom.shared.storage.s3.outbox;

public record S3ObjectDeleteOutboxPayload(
        String s3Key,
        String reason
) {
}
