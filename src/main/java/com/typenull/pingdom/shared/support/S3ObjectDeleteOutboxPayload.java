package com.typenull.pingdom.shared.support;

public record S3ObjectDeleteOutboxPayload(
        String s3Key,
        String reason
) {
}
