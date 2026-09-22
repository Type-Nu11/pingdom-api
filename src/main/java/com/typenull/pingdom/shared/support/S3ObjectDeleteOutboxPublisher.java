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

/** S3 key의 SHA-256 지문을 중복 방지 키로 사용해 비동기 삭제를 등록한다. 실제 객체 삭제는 handler가 수행한다. */
@Component
@RequiredArgsConstructor
public class S3ObjectDeleteOutboxPublisher {

    private static final String DEDUPLICATION_PREFIX = "S3_OBJECT_DELETE:";
    private static final String AGGREGATE_TYPE_FALLBACK = "S3_OBJECT";
    private static final String AGGREGATE_ID_FALLBACK = "UNKNOWN";
    private static final String REASON_FALLBACK = "UNSPECIFIED";

    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * 공백 key는 등록하지 않고 null을 반환한다. key가 같으면 사유·aggregate가 달라도 같은 중복 방지 키를 사용한다.
     * 호출자의 DB 트랜잭션과 이벤트 저장을 묶을 필요가 있으면 호출 측에서 트랜잭션을 제공해야 한다.
     */
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
