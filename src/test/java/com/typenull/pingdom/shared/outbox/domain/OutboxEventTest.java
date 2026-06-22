package com.typenull.pingdom.shared.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 22, 12, 0);

    @Test
    void failureSchedulesRetryWithFailureDetails() {
        OutboxEvent event = createClaimedEvent();

        event.fail(NOW, 5, NOW.plusSeconds(10), "temporary failure");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(event.getLastError()).isEqualTo("temporary failure");
    }

    @Test
    void failureIsPreservedAfterMaximumAttempts() {
        OutboxEvent event = createClaimedEvent();

        for (int attempt = 1; attempt <= 5; attempt++) {
            event.fail(NOW.plusSeconds(attempt), 5, NOW.plusMinutes(attempt), "failure-" + attempt);
            if (attempt < 5) {
                event.claim(NOW.plusMinutes(attempt));
            }
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.getLastError()).isEqualTo("failure-5");
    }

    @Test
    void succeededEventIgnoresDuplicateCompletion() {
        OutboxEvent event = createClaimedEvent();

        event.succeed(NOW);
        event.succeed(NOW.plusMinutes(1));

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(event.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void staleProcessingEventReturnsToRetryState() {
        OutboxEvent event = createClaimedEvent();

        event.recover(NOW.plusMinutes(5), 5, NOW.plusMinutes(5).plusSeconds(10), "timeout");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5).plusSeconds(10));
        assertThat(event.getProcessingStartedAt()).isNull();
    }

    private OutboxEvent createClaimedEvent() {
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:1:123456",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                "1",
                NOW.minusMinutes(1)
        );
        event.claim(NOW.minusSeconds(1));
        return event;
    }
}
