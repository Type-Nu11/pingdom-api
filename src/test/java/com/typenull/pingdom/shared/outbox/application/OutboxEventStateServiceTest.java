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
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

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

    @Test
    void operatorCanResetFailedEventForRetry() {
        OutboxEvent event = claimedEvent();
        when(outboxEventRepository.findById(event.getEventId())).thenReturn(Optional.of(event));

        for (int attempt = 0; attempt < 5; attempt++) {
            stateService.markFailed(event.getEventId(), new IllegalStateException("failure"));
            if (attempt < 4) {
                event.claim(event.getNextAttemptAt());
            }
        }

        boolean retried = stateService.retryFailedEvent(event.getEventId());

        assertThat(retried).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getLastError()).isNull();
    }

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
