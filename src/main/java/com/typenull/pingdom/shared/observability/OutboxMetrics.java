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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 상태별 현재 개수 gauge와 처리·재시도·복구의 누적 counter를 제공한다.
 * 상태별 DB 조회는 개별 실행되므로 gauge 전체가 동일 트랜잭션 시점의 스냅샷은 아니다.
 */
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
    }

    /** 애플리케이션 준비 시 및 30초 고정 지연마다 상태별 개수를 다시 읽는다. 첫 갱신 전 gauge는 0이다. */
    @EventListener(ApplicationReadyEvent.class)
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

    /** null 상태는 무시하고 FAILED는 최종 실패, 다른 상태는 retry로 집계한다. 상태 변경 자체는 수행하지 않는다. */
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

    public void recordManualRetry(OutboxEventType eventType, String result) {
        meterRegistry.counter(
                "pingdom.outbox.manual_retry",
                Tags.of(
                        "event_type", tagValue(eventType),
                        "result", safeTag(result)
                )
        ).increment();
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
