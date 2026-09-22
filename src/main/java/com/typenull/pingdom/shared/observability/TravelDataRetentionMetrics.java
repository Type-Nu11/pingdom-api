package com.typenull.pingdom.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/** 여행 데이터 정리의 성공·실패 실행 수와 삭제 건수 누계를 구분. 실패 사유는 예외 클래스명으로 기록. */
@Component
public class TravelDataRetentionMetrics {

    private static final String CLEANUP_METRIC = "pingdom.travel_data_retention.cleanup";
    private static final String DELETED_METRIC = "pingdom.travel_data_retention.deleted";

    private final MeterRegistry meterRegistry;

    public TravelDataRetentionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 삭제 0건도 성공 실행으로 세되 삭제 건수 counter는 양수일 때만 증가시킴. */
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
