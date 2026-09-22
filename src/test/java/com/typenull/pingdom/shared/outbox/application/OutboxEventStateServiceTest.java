package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T03:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventStateService stateService;

    /**
     * 최대 5회·기본 10초 backoff와 고정 Clock으로 Outbox 실패·수동 복구 서비스를 구성한다.
     */
    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(
                20,
                2,
                20,
                100,
                5,
                Duration.ofSeconds(10),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofDays(7)
        );
        stateService = new OutboxEventStateService(
                outboxEventRepository,
                properties,
                new OutboxBackoffPolicy(properties),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /**
     * 연속 실패의 시도 수가 1·2로 증가하고 고정 현재 시각 기준 다음 시도가 각각 10초·20초 뒤로 설정되는지 검증한다.
     */
    @Test
    void retryDelayUsesExponentialBackoff() {
        OutboxEvent event = claimedEvent();
        when(outboxEventRepository.findById(event.getEventId())).thenReturn(Optional.of(event));

        OutboxEventStatus status = stateService.markFailed(event.getEventId(), new IllegalStateException("temporary"));

        assertThat(status).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(10));

        event.claim(event.getNextAttemptAt());
        stateService.markFailed(event.getEventId(), new IllegalStateException("temporary"));

        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getNextAttemptAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(20));
    }

    /**
     * 5회 실패한 이벤트를 잠금 조회해 수동 재시도하면 이전 FAILED·이후 RETRY를 반환하고 시도 수와 오류를 초기화하는지 검증한다.
     */
    @Test
    void resetsFailedEventForRetry() {
        OutboxEvent event = claimedEvent();
        when(outboxEventRepository.findById(event.getEventId())).thenReturn(Optional.of(event));

        for (int attempt = 0; attempt < 5; attempt++) {
            stateService.markFailed(event.getEventId(), new IllegalStateException("failure"));
            if (attempt < 4) {
                event.claim(event.getNextAttemptAt());
            }
        }

        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        OutboxEventStateService.ManualRetryResult result = stateService.retryFailedEvent(event.getEventId());

        assertThat(result.outcome()).isEqualTo(OutboxEventStateService.ManualRetryOutcome.RETRIED);
        assertThat(result.before().status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(result.after().status()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getLastError()).isNull();
    }

    /**
     * PENDING 이벤트의 수동 재시도는 NOT_RETRYABLE이며 상태가 바뀌지 않는지 검증한다.
     */
    @Test
    void rejectsRetryForPendingEvent() {
        OutboxEvent event = OutboxEvent.create(
                "MAP_IMAGE_LIKED:10:21",
                OutboxEventType.MAP_IMAGE_LIKED,
                "{}",
                "MAP_IMAGE",
                "10",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        OutboxEventStateService.ManualRetryResult result = stateService.retryFailedEvent(event.getEventId());

        assertThat(result.outcome()).isEqualTo(OutboxEventStateService.ManualRetryOutcome.NOT_RETRYABLE);
        assertThat(result.before().status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    /**
     * 1분 전 생성하고 1초 전 선점한 좋아요 이벤트를 만들어 실패 처리가 가능한 상태를 제공한다.
     */
    private OutboxEvent claimedEvent() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        OutboxEvent event = OutboxEvent.create(
                "MAP_IMAGE_LIKED:10:20",
                OutboxEventType.MAP_IMAGE_LIKED,
                "{}",
                "MAP_IMAGE",
                "10",
                now.minusMinutes(1)
        );
        event.claim(now.minusSeconds(1));
        return event;
    }
}
