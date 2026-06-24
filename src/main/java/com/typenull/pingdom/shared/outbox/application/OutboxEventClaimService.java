package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {

    private static final List<OutboxEventStatus> READY_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.RETRY
    );

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final OutboxBackoffPolicy backoffPolicy;
    private final Clock outboxClock;
    private final OutboxMetrics outboxMetrics;

    @Transactional
    public List<String> claimReadyEvents() {
        LocalDateTime now = LocalDateTime.now(outboxClock);
        List<OutboxEvent> events = outboxEventRepository.findReadyEventsForUpdate(
                READY_STATUSES,
                now,
                PageRequest.of(0, properties.batchSize())
        );
        events.forEach(event -> event.claim(now));
        return events.stream().map(OutboxEvent::getEventId).toList();
    }

    @Transactional
    public int recoverStaleEvents() {
        LocalDateTime now = LocalDateTime.now(outboxClock);
        List<OutboxEvent> events = outboxEventRepository.findStaleProcessingEventsForUpdate(
                OutboxEventStatus.PROCESSING,
                now.minus(properties.processingTimeout()),
                PageRequest.of(0, properties.batchSize())
        );
        events.forEach(event -> event.recover(
                now,
                properties.maxAttempts(),
                now.plus(backoffPolicy.calculateDelay(event.getAttemptCount() + 1)),
                "PROCESSING timeout 이후 재처리 대상으로 복구"
        ));
        outboxMetrics.recordStaleRecovered(events.size());
        return events.size();
    }
}
