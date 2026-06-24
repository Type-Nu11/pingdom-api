package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;
    private final Map<OutboxEventStatus, AtomicLong> statusCounts = new ConcurrentHashMap<>();

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        this.meterRegistry = meterRegistry;
        this.outboxEventRepository = outboxEventRepository;
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            AtomicLong count = new AtomicLong(0L);
            statusCounts.put(status, count);
            Gauge.builder("pingdom.outbox.events", count, AtomicLong::get)
                    .description("Current outbox event count by status")
                    .tag("status", tagValue(status))
                    .register(meterRegistry);
        }
        refreshStatusCounts();
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void refreshStatusCounts() {
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            AtomicLong count = statusCounts.get(status);
            if (count != null) {
                count.set(outboxEventRepository.countByStatus(status));
            }
        }
    }

    public void recordSuccess(OutboxEventType eventType, String handler) {
        counter(eventType, handler, "success").increment();
    }

    public void recordFailure(OutboxEventType eventType, String handler, OutboxEventStatus status) {
        if (status == null) {
            return;
        }
        String result = status == OutboxEventStatus.FAILED ? "failed" : "retry";
        counter(eventType, handler, result).increment();
    }

    public void recordMaxAttemptsExceeded(OutboxEventType eventType, String handler) {
        meterRegistry.counter(
                "pingdom.outbox.max_attempts_exceeded",
                Tags.of(
                        "event_type", tagValue(eventType),
                        "handler", safeTag(handler)
                )
        ).increment();
    }

    public void recordStaleRecovered(int count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter("pingdom.outbox.stale_recovered").increment(count);
    }

    private io.micrometer.core.instrument.Counter counter(
            OutboxEventType eventType,
            String handler,
            String result
    ) {
        return meterRegistry.counter(
                "pingdom.outbox.processed",
                Tags.of(
                        "event_type", tagValue(eventType),
                        "handler", safeTag(handler),
                        "result", result
                )
        );
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
