package com.typenull.pingdom.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class TravelDataRetentionMetrics {

    private static final String CLEANUP_METRIC = "pingdom.travel_data_retention.cleanup";
    private static final String DELETED_METRIC = "pingdom.travel_data_retention.deleted";

    private final MeterRegistry meterRegistry;

    public TravelDataRetentionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(int deletedCount) {
        meterRegistry.counter(CLEANUP_METRIC, Tags.of("result", "success", "reason", "none")).increment();
        if (deletedCount > 0) {
            meterRegistry.counter(DELETED_METRIC).increment(deletedCount);
        }
    }

    public void recordFailure(Exception exception) {
        meterRegistry.counter(
                CLEANUP_METRIC,
                Tags.of("result", "failure", "reason", exception.getClass().getSimpleName())
        ).increment();
    }
}
