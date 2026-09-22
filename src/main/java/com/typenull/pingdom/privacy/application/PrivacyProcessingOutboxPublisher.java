package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** 개인정보 처리 작업과 같은 트랜잭션에서 재처리 가능한 감사 이력 이벤트를 기록. */
@Component
@RequiredArgsConstructor
public class PrivacyProcessingOutboxPublisher {

    private static final String DEDUPLICATION_PREFIX = "PRIVACY_PROCESSING:";
    private static final String AGGREGATE_TYPE_PREFIX = "PRIVACY:";

    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    public void publish(PrivacyProcessingEvent event) {
        publish(
                event.subjectUserId(),
                event.actorUserId(),
                event.actorType(),
                event.action(),
                event.details()
        );
    }

    /**
     * 한 번의 bulk 입력에서 같은 사용자 ID는 한 번만 발행.
     * 개별 이벤트에는 매번 새 UUID를 부여하므로 별도의 publish 재호출은 새로운 처리 이력으로 기록됨.
     */
    public void publish(PrivacyProcessingBulkEvent event) {
        event.subjectUserIds().stream()
                .distinct()
                .forEach(subjectUserId -> publish(
                        subjectUserId,
                        event.actorUserId(),
                        event.actorType(),
                        event.action(),
                        event.details()
                ));
    }

    private void publish(
            Long subjectUserId,
            Long actorUserId,
            com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType actorType,
            com.typenull.pingdom.privacy.domain.PrivacyProcessingAction action,
            String details
    ) {
        if (subjectUserId == null) {
            throw new IllegalArgumentException("개인정보 처리 이력 대상 사용자 ID가 필요합니다.");
        }

        outboxEventPublisher.publish(
                DEDUPLICATION_PREFIX + UUID.randomUUID(),
                OutboxEventType.PRIVACY_PROCESSING_RECORDED,
                new PrivacyProcessingOutboxPayload(
                        subjectUserId,
                        actorUserId,
                        actorType,
                        action,
                        details,
                        MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
                        LocalDateTime.now(clock)
                ),
                AGGREGATE_TYPE_PREFIX + action.name(),
                String.valueOf(subjectUserId)
        );
    }
}
