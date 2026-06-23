package com.typenull.pingdom.shared.support;

import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class S3ObjectDeleteOutboxPublisher {

    private static final String DEDUPLICATION_PREFIX = "S3_OBJECT_DELETE:";
    private static final String AGGREGATE_TYPE_FALLBACK = "S3_OBJECT";
    private static final String AGGREGATE_ID_FALLBACK = "UNKNOWN";
    private static final String REASON_FALLBACK = "UNSPECIFIED";

    private final OutboxEventPublisher outboxEventPublisher;

    public String publish(
            String s3Key,
            String aggregateType,
            String aggregateId,
            String reason
    ) {
        if (!StringUtils.hasText(s3Key)) {
            return null;
        }

        String normalizedKey = s3Key.trim();
        return outboxEventPublisher.publish(
                DEDUPLICATION_PREFIX + sha256(normalizedKey),
                OutboxEventType.S3_OBJECT_DELETE_REQUESTED,
                new S3ObjectDeleteOutboxPayload(normalizedKey, normalize(reason, REASON_FALLBACK)),
                normalize(aggregateType, AGGREGATE_TYPE_FALLBACK),
                normalize(aggregateId, AGGREGATE_ID_FALLBACK)
        );
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("S3 삭제 Outbox deduplication key 생성에 실패했습니다.", exception);
        }
    }
}
