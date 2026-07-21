package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class ScoutFieldReportMetrics {

    private final MeterRegistry meterRegistry;

    public ScoutFieldReportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordReportSubmitted(ScoutFieldReportType reportType) {
        meterRegistry.counter(
                "pingdom.scout.field_report_submitted",
                Tags.of("report_type", tagValue(reportType))
        ).increment();
    }

    public void recordReportStatusUpdate(
            ScoutFieldReportStatus fromStatus,
            ScoutFieldReportStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.scout.field_report_status_updates",
                Tags.of("from_status", tagValue(fromStatus), "to_status", tagValue(toStatus))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
