package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class PlaceVisitDecisionMetrics {

    private final MeterRegistry meterRegistry;

    public PlaceVisitDecisionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordViewed(PlaceOperatingStatus operatingStatus) {
        // placeId를 태그로 쓰지 않아 고카디널리티 메트릭이 생성되는 것을 막는다.
        meterRegistry.counter(
                "pingdom.place.visit_decision_views",
                Tags.of("operating_status", tagValue(operatingStatus))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
