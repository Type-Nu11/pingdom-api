package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실행 용량 내 선점 및 만료 lease 복구를 짧은 DB 트랜잭션으로 수행한다. */
@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {

    private static final List<OutboxEventStatus> READY_STATUSES =
            List.of(OutboxEventStatus.PENDING, OutboxEventStatus.RETRY);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final OutboxBackoffPolicy backoffPolicy;
    private final OutboxMetrics outboxMetrics;

    @Transactional
    public List<OutboxClaim> claimReadyEvents(int availableSlots) {
        if (availableSlots <= 0) {
            return List.of();
        }
        LocalDateTime now = outboxEventRepository.databaseNow();
        List<OutboxEvent> events = outboxEventRepository.findReadyEventsForUpdate(
                READY_STATUSES, now, PageRequest.of(0, Math.min(availableSlots, properties.batchSize())));
        return events.stream().map(event -> {
            String token = UUID.randomUUID().toString();
            event.claim(token, now, now.plus(properties.processingTimeout()));
            return new OutboxClaim(event.getEventId(), token);
        }).toList();
    }

    @Transactional
    public int recoverStaleEvents() {
        LocalDateTime now = outboxEventRepository.databaseNow();
        List<OutboxEvent> events = outboxEventRepository.findStaleProcessingEventsForUpdate(
                OutboxEventStatus.PROCESSING, now, PageRequest.of(0, properties.batchSize()));
        events.forEach(event -> event.recover(
                now, properties.maxAttempts(),
                now.plus(backoffPolicy.calculateDelay(event.getAttemptCount() + 1)),
                "Outbox lease 만료 이후 재처리 대상으로 복구"));
        outboxMetrics.recordStaleRecovered(events.size());
        return events.size();
    }
}
