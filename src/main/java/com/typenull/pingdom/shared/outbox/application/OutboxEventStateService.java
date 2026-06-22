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
    public boolean retryFailedEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.FAILED) {
            return false;
        }
        event.retry(LocalDateTime.now(outboxClock));
        return true;
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
}
