package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/** 방문 판단 정보 조회를 영업 상태별로 집계. 장소 식별자 없이 상태만 태그로 사용. */
@Component
public class PlaceVisitDecisionMetrics {

    private final MeterRegistry meterRegistry;

    public PlaceVisitDecisionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordViewed(PlaceOperatingStatus operatingStatus) {
        // placeId를 태그로 쓰지 않아 고카디널리티 메트릭이 생성되는 것을 차단.
        meterRegistry.counter(
                "pingdom.place.visit_decision_views",
                Tags.of("operating_status", tagValue(operatingStatus))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
