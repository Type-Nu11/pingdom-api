package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LegacyApiUsageMetricsTest {

    private static final Set<String> EXPECTED_ENDPOINT_TAGS = Set.of(
            "POST /places/coordinates",
            "POST /places/upload",
            "POST /map/posts (coordinate place creation)"
    );

    @Test
    void registersAllFixedLegacyEndpointsAndRecordsRequests() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LegacyApiUsageMetrics metrics = new LegacyApiUsageMetrics(registry);

        assertThat(registry.find(LegacyApiUsageMetrics.METRIC_NAME).counters())
                .extracting(this::endpointTag)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ENDPOINT_TAGS);

        for (LegacyApiEndpoint endpoint : LegacyApiEndpoint.values()) {
            metrics.record(endpoint);
        }

        for (LegacyApiEndpoint endpoint : LegacyApiEndpoint.values()) {
            assertThat(registry.find(LegacyApiUsageMetrics.METRIC_NAME)
                    .tag("method", endpoint.method())
                    .tag("path", endpoint.path())
                    .counter()
                    .count()).isEqualTo(1.0d);
        }
    }

    private String endpointTag(Counter counter) {
        return counter.getId().getTag("method") + " " + counter.getId().getTag("path");
    }
}
