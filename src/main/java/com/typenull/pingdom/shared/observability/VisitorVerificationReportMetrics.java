package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class VisitorVerificationReportMetrics {

    private final MeterRegistry meterRegistry;

    public VisitorVerificationReportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordReportSubmitted(VisitorVerificationReportType reportType) {
        meterRegistry.counter(
                "pingdom.visitor.verification_report_submitted",
                Tags.of("report_type", tagValue(reportType))
        ).increment();
    }

    public void recordReportStatusUpdate(
            VisitorVerificationReportStatus fromStatus,
            VisitorVerificationReportStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.visitor.verification_report_status_updates",
                Tags.of("from_status", tagValue(fromStatus), "to_status", tagValue(toStatus))
        ).increment();
    }

    public void recordCorrectionSubmitted() {
        meterRegistry.counter("pingdom.visitor.verification_report_correction_submitted").increment();
    }

    public void recordCorrectionStatusUpdate(
            VisitorVerificationReportCorrectionStatus fromStatus,
            VisitorVerificationReportCorrectionStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.visitor.verification_report_correction_status_updates",
                Tags.of("from_status", tagValue(fromStatus), "to_status", tagValue(toStatus))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
