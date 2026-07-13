package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TravelDataRetentionMetricsTest {

    @Test
    void recordsCleanupSuccessAndFailure() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TravelDataRetentionMetrics metrics = new TravelDataRetentionMetrics(meterRegistry);

        metrics.recordSuccess(3);
        metrics.recordFailure(new IllegalStateException());

        assertThat(meterRegistry.get("pingdom.travel_data_retention.cleanup")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("pingdom.travel_data_retention.cleanup")
                .tag("result", "failure")
                .tag("reason", "IllegalStateException")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("pingdom.travel_data_retention.deleted")
                .counter()
                .count()).isEqualTo(3.0);
    }
}
