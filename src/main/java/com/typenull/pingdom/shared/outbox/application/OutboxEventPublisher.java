package com.typenull.pingdom.shared.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final List<OutboxEventStatus> COALESCING_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.RETRY
    );

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock outboxClock;

    public String publish(
            String deduplicationKey,
            OutboxEventType eventType,
            Object payload,
            String aggregateType,
            String aggregateId
    ) {
        if (outboxEventRepository.existsByDeduplicationKey(deduplicationKey)) {
            return null;
        }

        OutboxEvent event = OutboxEvent.create(
                deduplicationKey,
                eventType,
                serialize(payload),
                aggregateType,
                aggregateId,
                LocalDateTime.now(outboxClock)
        );
        return outboxEventRepository.save(event).getEventId();
    }

    /**
     * 동일 aggregate의 쓰기 잠금을 보유한 호출자에서 사용해야 조회와 발행 사이의 경쟁을 막을 수 있습니다.
     */
    public String publishCoalesced(
            String deduplicationKey,
            OutboxEventType eventType,
            Object payload,
            String aggregateType,
            String aggregateId
    ) {
        if (outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
                eventType,
                aggregateType,
                aggregateId,
                COALESCING_STATUSES
        )) {
            return null;
        }
        return publish(deduplicationKey, eventType, payload, aggregateType, aggregateId);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox payload 직렬화에 실패했습니다.", exception);
        }
    }
}
