package com.typenull.pingdom.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LegacyApiUsageMetrics {

    static final String METRIC_NAME = "pingdom.api.legacy.requests";

    private final Map<LegacyApiEndpoint, Counter> counters;

    public LegacyApiUsageMetrics(MeterRegistry meterRegistry) {
        EnumMap<LegacyApiEndpoint, Counter> registeredCounters = new EnumMap<>(LegacyApiEndpoint.class);
        for (LegacyApiEndpoint endpoint : LegacyApiEndpoint.values()) {
            registeredCounters.put(endpoint, Counter.builder(METRIC_NAME)
                    .description("Legacy API request count by fixed endpoint template")
                    .tag("method", endpoint.method())
                    .tag("path", endpoint.path())
                    .register(meterRegistry));
        }
        this.counters = Map.copyOf(registeredCounters);
    }

    public void record(LegacyApiEndpoint endpoint) {
        counters.get(endpoint).increment();
    }
}
