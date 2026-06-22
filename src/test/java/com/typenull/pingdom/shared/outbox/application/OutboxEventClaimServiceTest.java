package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T03:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventClaimService claimService;

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
        claimService = new OutboxEventClaimService(
                outboxEventRepository,
                properties,
                new OutboxBackoffPolicy(properties),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void staleRecoveryUsesExponentialBackoffFromNextAttempt() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:1:123456",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                "1",
                now.minusMinutes(10)
        );
        event.claim(now.minusMinutes(9));
        event.fail(now.minusMinutes(8), 5, now.minusMinutes(7), "first failure");
        event.claim(now.minusMinutes(6));
        when(outboxEventRepository.findStaleProcessingEventsForUpdate(
                eq(OutboxEventStatus.PROCESSING),
                eq(now.minusMinutes(5)),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        int recoveredCount = claimService.recoverStaleEvents();

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(20));
    }
}
