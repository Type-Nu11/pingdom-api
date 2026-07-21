package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class PlaceOperatingNoticeMetrics {

    private final MeterRegistry meterRegistry;

    public PlaceOperatingNoticeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCreated(PlaceOperatingNoticeType noticeType, PlaceOperatingNoticeStatus status) {
        meterRegistry.counter(
                "pingdom.place.operating_notice_created",
                Tags.of(
                        "notice_type", tagValue(noticeType),
                        "status", tagValue(status)
                )
        ).increment();
    }

    public void recordStatusUpdate(
            PlaceOperatingNoticeType noticeType,
            PlaceOperatingNoticeStatus fromStatus,
            PlaceOperatingNoticeStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.place.operating_notice_status_updates",
                Tags.of(
                        "notice_type", tagValue(noticeType),
                        "from_status", tagValue(fromStatus),
                        "to_status", tagValue(toStatus)
                )
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
