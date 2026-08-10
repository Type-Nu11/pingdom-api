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

    public void claim(LocalDateTime now) {
        if (status != OutboxEventStatus.PENDING && status != OutboxEventStatus.RETRY) {
            return;
        }
        status = OutboxEventStatus.PROCESSING;
        processingStartedAt = now;
        updatedAt = now;
    }

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

    public void recover(
            LocalDateTime now,
            int maxAttempts,
            LocalDateTime nextAttemptAt,
            String reason
    ) {
        fail(now, maxAttempts, nextAttemptAt, reason);
    }

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

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= LAST_ERROR_MAX_LENGTH
                ? value
                : value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
