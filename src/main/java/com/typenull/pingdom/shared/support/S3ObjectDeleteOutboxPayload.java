package com.typenull.pingdom.shared.support;

/** 삭제 대상 S3 key와 운영 추적용 사유를 담음. URL 대신 key를 사용하며 handler가 빈 key를 거부. */
public record S3ObjectDeleteOutboxPayload(
        String s3Key,
        String reason
) {
}
