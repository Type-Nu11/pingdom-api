package com.typenull.pingdom.privacy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivacyProcessingOutboxPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    /**
     * 사용자 export 이벤트가 개인정보 Outbox 타입·집계 키로 발행되고 대상·행위자·행위·UTC 발생 시각이 payload에 담기는지 검증한다.
     */
    @Test
    void publishesUserPrivacyEvent() {
        PrivacyProcessingOutboxPublisher publisher = new PrivacyProcessingOutboxPublisher(outboxEventPublisher, CLOCK);

        publisher.publish(PrivacyProcessingEvent.userAction(
                10L,
                PrivacyProcessingAction.EXPORT_REQUESTED,
                "사용자 데이터 export 요청"
        ));

        ArgumentCaptor<PrivacyProcessingOutboxPayload> payloadCaptor =
                ArgumentCaptor.forClass(PrivacyProcessingOutboxPayload.class);
        verify(outboxEventPublisher).publish(
                startsWith("PRIVACY_PROCESSING:"),
                eq(OutboxEventType.PRIVACY_PROCESSING_RECORDED),
                payloadCaptor.capture(),
                eq("PRIVACY:EXPORT_REQUESTED"),
                eq("10")
        );
        assertThat(payloadCaptor.getValue())
                .extracting(
                        PrivacyProcessingOutboxPayload::subjectUserId,
                        PrivacyProcessingOutboxPayload::actorUserId,
                        PrivacyProcessingOutboxPayload::action,
                        PrivacyProcessingOutboxPayload::occurredAt
                )
                .containsExactly(
                        10L,
                        10L,
                        PrivacyProcessingAction.EXPORT_REQUESTED,
                        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)
                );
    }

    /**
     * 대상 10·10·20의 삭제 이벤트가 집계 ID 10·20으로 두 번만 발행되어 중복 사용자를 제거하는지 검증한다.
     */
    @Test
    void deduplicatesBulkPrivacyTargets() {
        PrivacyProcessingOutboxPublisher publisher = new PrivacyProcessingOutboxPublisher(outboxEventPublisher, CLOCK);

        publisher.publish(PrivacyProcessingBulkEvent.systemAction(
                List.of(10L, 10L, 20L),
                PrivacyProcessingAction.DELETED,
                "보존기간 만료에 따른 탈퇴 사용자 최종 삭제"
        ));

        ArgumentCaptor<String> aggregateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventPublisher, times(2)).publish(
                startsWith("PRIVACY_PROCESSING:"),
                eq(OutboxEventType.PRIVACY_PROCESSING_RECORDED),
                any(),
                eq("PRIVACY:DELETED"),
                aggregateIdCaptor.capture()
        );
        assertThat(aggregateIdCaptor.getAllValues()).containsExactly("10", "20");
    }
}
