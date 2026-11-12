package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OutboxPropertiesTest {
    @Test
    void bindsOperationalDefaultsWithCanonicalRecordConstructor() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "outbox.batch-size", "20", "outbox.worker-concurrency", "4",
                "outbox.worker-queue-capacity", "40", "outbox.cleanup-batch-size", "100",
                "outbox.max-attempts", "5", "outbox.base-backoff", "PT10S",
                "outbox.max-backoff", "PT10M", "outbox.processing-timeout", "PT5M",
                "outbox.retention", "P7D")));

        OutboxProperties properties = binder.bind("outbox", Bindable.of(OutboxProperties.class)).get();

        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxExecutionTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.isTimingValid()).isTrue();
    }

    @Test
    void rejectsZeroNegativeAndInconsistentPeriods() {
        assertThat(properties(Duration.ZERO, Duration.ofMinutes(5), Duration.ofMinutes(30)).isTimingValid()).isFalse();
        assertThat(properties(Duration.ofSeconds(-1), Duration.ofMinutes(5), Duration.ofMinutes(30)).isTimingValid()).isFalse();
        assertThat(properties(Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(30)).isTimingValid()).isFalse();
        assertThat(properties(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(1)).isTimingValid()).isFalse();
        assertThat(properties(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)).isTimingValid()).isTrue();
    }

    private OutboxProperties properties(Duration heartbeat, Duration lease, Duration maximum) {
        return new OutboxProperties(20, 4, 40, 100, 5, Duration.ofSeconds(10),
                Duration.ofMinutes(10), lease, Duration.ofDays(7), heartbeat, maximum);
    }
}
