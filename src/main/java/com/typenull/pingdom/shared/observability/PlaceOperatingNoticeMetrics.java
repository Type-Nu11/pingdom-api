package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/** 운영 공지 생성과 상태 변경 횟수를 공지 종류 및 전후 상태별로 집계. */
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
