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

/** 처리할 이벤트 선점과 오래된 PROCESSING 복구를 각각 짧은 DB 트랜잭션으로 수행. */
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

    /**
     * 현재 시각까지 실행 기한이 된 PENDING/RETRY를 배치 크기만큼 잠금 조회하고 PROCESSING으로 변경.
     * 실제 handler 실행은 선점 트랜잭션 밖의 worker가 담당.
     */
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

    /**
     * processingTimeout보다 오래 진행 중인 이벤트를 잠금 조회해 실패 횟수와 backoff를 반영.
     * 실패 한도에 도달하면 FAILED가 될 수 있으며 복구 조회 건수를 반환.
     */
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
