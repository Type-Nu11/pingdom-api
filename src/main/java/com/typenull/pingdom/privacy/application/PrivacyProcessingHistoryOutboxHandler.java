package com.typenull.pingdom.privacy.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 재시도되는 Outbox 이벤트를 개인정보 처리 감사 이력으로 멱등 저장합니다. */
@Component
@RequiredArgsConstructor
public class PrivacyProcessingHistoryOutboxHandler implements OutboxEventHandler {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.PRIVACY_PROCESSING_RECORDED;
    }

    @Override
    @Transactional
    public void handle(String eventId, String payload) {
        PrivacyProcessingOutboxPayload event = deserialize(payload);
        validate(event);

        if (privacyProcessingHistoryRepository.existsByOutboxEventIdAndSubjectUserId(eventId, event.subjectUserId())) {
            return;
        }

        privacyProcessingHistoryRepository.save(PrivacyProcessingHistory.builder()
                .subjectUserId(event.subjectUserId())
                .outboxEventId(eventId)
                .actorUserId(event.actorUserId())
                .actorType(event.actorType())
                .action(event.action())
                .details(event.details())
                .requestId(event.requestId())
                .createdAt(event.occurredAt())
                .build());
    }

    private PrivacyProcessingOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PrivacyProcessingOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("개인정보 처리 이력 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }

    private void validate(PrivacyProcessingOutboxPayload event) {
        if (event.subjectUserId() == null || event.actorType() == null || event.action() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("개인정보 처리 이력 Outbox payload에 필수 값이 없습니다.");
        }
    }
}
