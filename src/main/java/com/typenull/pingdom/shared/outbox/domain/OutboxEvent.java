package com.typenull.pingdom.shared.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중복 키·payload와 처리 상태를 저장하는 Outbox 행이다.
 * 실패 때 attemptCount가 증가하며 version으로 동시 DB 갱신을 감지한다. 외부 handler 중복 실행을 막는 토큰은 없다.
 */
@Entity
@Table(
        name = "outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_deduplication_key", columnNames = "deduplication_key")
        },
        indexes = {
                @Index(
                        name = "idx_outbox_event_ready",
                        columnList = "status, next_attempt_at, created_at"
                ),
                @Index(
                        name = "idx_outbox_event_processing",
                        columnList = "status, processing_started_at"
                ),
                @Index(
                        name = "idx_outbox_event_status_created",
                        columnList = "status, created_at DESC, event_id DESC"
                ),
                @Index(
                        name = "idx_outbox_event_type_created",
                        columnList = "event_type, created_at DESC, event_id DESC"
                ),
                @Index(
                        name = "idx_outbox_event_aggregate_created",
                        columnList = "aggregate_type, aggregate_id, created_at DESC, event_id DESC"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int LAST_ERROR_MAX_LENGTH = 2000;

    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "deduplication_key", length = 200, nullable = false, updatable = false)
    private String deduplicationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false, updatable = false)
    private OutboxEventType eventType;

    @Column(name = "payload", columnDefinition = "text", nullable = false, updatable = false)
    private String payload;

    @Column(name = "aggregate_type", length = 50, nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 100, nullable = false, updatable = false)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    /** 새 UUID와 PENDING 상태로 생성하고 현재 시각부터 선점할 수 있게 한다. */
    public static OutboxEvent create(
            String deduplicationKey,
            OutboxEventType eventType,
            String payload,
            String aggregateType,
            String aggregateId,
            LocalDateTime now
    ) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID().toString();
        event.deduplicationKey = deduplicationKey;
        event.eventType = eventType;
        event.payload = payload;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.status = OutboxEventStatus.PENDING;
        event.nextAttemptAt = now;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    /** PENDING/RETRY만 PROCESSING으로 전환해 시작 시각을 기록한다. 이 단계에서는 시도 횟수를 증가시키지 않는다. */
    public void claim(LocalDateTime now) {
        if (status != OutboxEventStatus.PENDING && status != OutboxEventStatus.RETRY) {
            return;
        }
        status = OutboxEventStatus.PROCESSING;
        processingStartedAt = now;
        updatedAt = now;
    }

    /** PROCESSING만 성공으로 바꾸고 처리 시작 시각·오류를 비운다. 다른 상태에서는 변경하지 않는다. */
    public void succeed(LocalDateTime now) {
        if (status != OutboxEventStatus.PROCESSING) {
            return;
        }
        status = OutboxEventStatus.SUCCEEDED;
        processedAt = now;
        processingStartedAt = null;
        lastError = null;
        updatedAt = now;
    }

    /**
     * PROCESSING 실패 횟수를 증가시키고 한도 도달 시 FAILED, 아니면 지정 시각의 RETRY로 전환한다.
     * 오류는 최대 2,000자로 잘라 보관하며 처리 중 시각을 비운다.
     */
    public void fail(LocalDateTime now, int maxAttempts, LocalDateTime nextAttemptAt, String errorMessage) {
        if (status != OutboxEventStatus.PROCESSING) {
            return;
        }
        attemptCount++;
        lastError = truncate(errorMessage);
        processingStartedAt = null;
        updatedAt = now;

        if (attemptCount >= maxAttempts) {
            status = OutboxEventStatus.FAILED;
            this.nextAttemptAt = now;
            return;
        }

        status = OutboxEventStatus.RETRY;
        this.nextAttemptAt = nextAttemptAt;
    }

    /** 고착 복구도 실패와 같은 횟수·한도 규칙을 적용해 무한 복구 반복을 제한한다. */
    public void recover(
            LocalDateTime now,
            int maxAttempts,
            LocalDateTime nextAttemptAt,
            String reason
    ) {
        fail(now, maxAttempts, nextAttemptAt, reason);
    }

    /** FAILED만 수동 재시도 상태로 돌리고 실패 횟수·처리 시각·오류를 초기화한다. */
    public void retry(LocalDateTime now) {
        if (status != OutboxEventStatus.FAILED) {
            return;
        }
        status = OutboxEventStatus.RETRY;
        attemptCount = 0;
        nextAttemptAt = now;
        processedAt = null;
        processingStartedAt = null;
        lastError = null;
        updatedAt = now;
    }

    /** DB 오류 필드 길이 제한에 맞춰 앞 2,000자만 남기며 null은 유지한다. */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= LAST_ERROR_MAX_LENGTH
                ? value
                : value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
