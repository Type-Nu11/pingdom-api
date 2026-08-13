package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LegacyApiUsageMetricsTest {

    @Test
    void registersAllFixedLegacyEndpointsAndRecordsRequests() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LegacyApiUsageMetrics metrics = new LegacyApiUsageMetrics(registry);

        assertThat(registry.find(LegacyApiUsageMetrics.METRIC_NAME).counters())
                .hasSize(LegacyApiEndpoint.values().length)
                .extracting(this::endpointTag)
                .containsExactlyInAnyOrder(Arrays.stream(LegacyApiEndpoint.values())
                        .map(endpoint -> endpoint.method() + " " + endpoint.path())
                        .toArray(String[]::new));

        metrics.record(LegacyApiEndpoint.POST_CREATE);

        assertThat(registry.find(LegacyApiUsageMetrics.METRIC_NAME)
                .tag("method", "POST")
                .tag("path", "/map/post/create")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.find(LegacyApiUsageMetrics.METRIC_NAME)
                .tag("method", "GET")
                .tag("path", "/place")
                .counter()
                .count()).isZero();
    }

    private String endpointTag(Counter counter) {
        return counter.getId().getTag("method") + " " + counter.getId().getTag("path");
    }
}
