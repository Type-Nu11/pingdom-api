package com.typenull.pingdom.shared.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

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

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox payload 직렬화에 실패했습니다.", exception);
        }
    }
}
