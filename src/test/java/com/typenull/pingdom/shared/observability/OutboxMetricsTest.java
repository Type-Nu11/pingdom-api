package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxMetricsTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void constructorDoesNotQueryStatusCounts() {
        new OutboxMetrics(new SimpleMeterRegistry(), outboxEventRepository);

        verify(outboxEventRepository, never()).countByStatus(any(OutboxEventStatus.class));
    }

    @Test
    void refreshStatusCountsUpdatesGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(outboxEventRepository.countByStatus(any(OutboxEventStatus.class))).thenReturn(3L);
        OutboxMetrics outboxMetrics = new OutboxMetrics(meterRegistry, outboxEventRepository);

        outboxMetrics.refreshStatusCounts();

        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            assertThat(meterRegistry.get("pingdom.outbox.events")
                    .tag("status", status.name())
                    .gauge()
                    .value()).isEqualTo(3.0);
        }
        verify(outboxEventRepository, times(OutboxEventStatus.values().length))
                .countByStatus(any(OutboxEventStatus.class));
    }

    @Test
    void manualRetryMetricUsesBoundedEventTypeAndResultTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxMetrics outboxMetrics = new OutboxMetrics(meterRegistry, outboxEventRepository);

        outboxMetrics.recordManualRetry(OutboxEventType.EMAIL_VERIFICATION_REQUESTED, "success");

        assertThat(meterRegistry.get("pingdom.outbox.manual_retry")
                .tag("event_type", OutboxEventType.EMAIL_VERIFICATION_REQUESTED.name())
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
