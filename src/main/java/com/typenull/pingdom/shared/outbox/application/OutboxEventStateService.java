package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventStateService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final OutboxBackoffPolicy backoffPolicy;
    private final Clock outboxClock;

    @Transactional(readOnly = true)
    public OutboxEventSnapshot findProcessingEvent(String eventId) {
        return outboxEventRepository.findById(eventId)
                .filter(event -> event.getStatus() == OutboxEventStatus.PROCESSING)
                .map(OutboxEventSnapshot::from)
                .orElse(null);
    }

    @Transactional
    public void markSucceeded(String eventId) {
        outboxEventRepository.findById(eventId)
                .ifPresent(event -> event.succeed(LocalDateTime.now(outboxClock)));
    }

    @Transactional
    public OutboxEventStatus markFailed(String eventId, Throwable failure) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.PROCESSING) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(outboxClock);
        event.fail(
                now,
                properties.maxAttempts(),
                now.plus(backoffPolicy.calculateDelay(event.getAttemptCount() + 1)),
                failureMessage(failure)
        );
        return event.getStatus();
    }

    @Transactional
    public ManualRetryResult retryFailedEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findByEventIdForUpdate(eventId).orElse(null);
        if (event == null) {
            return ManualRetryResult.notFound();
        }

        OutboxEventOperationSnapshot before = OutboxEventOperationSnapshot.from(event);
        if (event.getStatus() != OutboxEventStatus.FAILED) {
            return ManualRetryResult.notRetryable(before);
        }

        event.retry(LocalDateTime.now(outboxClock));
        return ManualRetryResult.retried(before, OutboxEventOperationSnapshot.from(event));
    }

    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record OutboxEventSnapshot(
            String eventId,
            com.typenull.pingdom.shared.outbox.domain.OutboxEventType eventType,
            String payload,
            String aggregateType,
            String aggregateId,
            int attemptCount
    ) {
        private static OutboxEventSnapshot from(OutboxEvent event) {
            return new OutboxEventSnapshot(
                    event.getEventId(),
                    event.getEventType(),
                    event.getPayload(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAttemptCount()
            );
        }
    }

    public enum ManualRetryOutcome {
        RETRIED,
        NOT_FOUND,
        NOT_RETRYABLE
    }

    public record ManualRetryResult(
            ManualRetryOutcome outcome,
            OutboxEventOperationSnapshot before,
            OutboxEventOperationSnapshot after
    ) {
        private static ManualRetryResult retried(
                OutboxEventOperationSnapshot before,
                OutboxEventOperationSnapshot after
        ) {
            return new ManualRetryResult(ManualRetryOutcome.RETRIED, before, after);
        }

        private static ManualRetryResult notFound() {
            return new ManualRetryResult(ManualRetryOutcome.NOT_FOUND, null, null);
        }

        private static ManualRetryResult notRetryable(OutboxEventOperationSnapshot snapshot) {
            return new ManualRetryResult(ManualRetryOutcome.NOT_RETRYABLE, snapshot, snapshot);
        }
    }

    public record OutboxEventOperationSnapshot(
            String eventId,
            com.typenull.pingdom.shared.outbox.domain.OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            OutboxEventStatus status,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            LocalDateTime processingStartedAt,
            LocalDateTime processedAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        private static OutboxEventOperationSnapshot from(OutboxEvent event) {
            return new OutboxEventOperationSnapshot(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getStatus(),
                    event.getAttemptCount(),
                    event.getNextAttemptAt(),
                    event.getProcessingStartedAt(),
                    event.getProcessedAt(),
                    event.getLastError(),
                    event.getCreatedAt(),
                    event.getUpdatedAt()
            );
        }
    }
}
