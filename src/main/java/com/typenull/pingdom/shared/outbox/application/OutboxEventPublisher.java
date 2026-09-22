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

/**
 * 중복 키와 JSON payload를 갖는 Outbox 행을 저장.
 * 자체 트랜잭션 선언이 없으므로 업무 변경과 원자적으로 저장하려면 호출자가 트랜잭션을 제공해야 함.
 */
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

    /**
     * 같은 중복 키가 이미 있으면 null, 새로 저장하면 이벤트 ID를 반환.
     * 존재 조회만으로 동시 발행 경쟁을 막지는 못하며 DB 유일 제약과 호출자의 잠금 규칙이 별도로 필요.
     */
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
     * 동일 aggregate의 쓰기 잠금을 보유한 호출자에서 사용해야 조회와 발행 사이의 경쟁을 막을 수 있음.
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

    /** payload를 JSON으로 직렬화하며 실패를 IllegalStateException으로 전달해 발행을 중단. */
    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox payload 직렬화에 실패했습니다.", exception);
        }
    }
}
