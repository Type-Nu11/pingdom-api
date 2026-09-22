package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/** 장소 노출 상태의 from/to 전이 호출 횟수를 기록한다. null 상태는 unknown 태그로 구분한다. */
@Component
public class PlaceDiscoveryMetrics {

    private final MeterRegistry meterRegistry;

    public PlaceDiscoveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordStatusUpdate(PlaceDiscoveryStatus fromStatus, PlaceDiscoveryStatus toStatus) {
        meterRegistry.counter(
                "pingdom.place.discovery_status_updates",
                Tags.of(
                        "from_status", tagValue(fromStatus),
                        "to_status", tagValue(toStatus)
                )
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
