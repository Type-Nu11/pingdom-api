package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/** Scout 제보 종류와 제보·프로필·활동 자격 상태 전이 횟수를 기록. 저장 성공 여부는 호출 시점에 달려 있음. */
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

    public void recordProfileStatusUpdate(ScoutProfileStatus fromStatus, ScoutProfileStatus toStatus) {
        meterRegistry.counter(
                "pingdom.scout.profile_status_updates",
                Tags.of("from_status", tagValue(fromStatus), "to_status", tagValue(toStatus))
        ).increment();
    }

    public void recordActivityEligibilityStatusUpdate(
            ScoutActivityEligibilityStatus fromStatus,
            ScoutActivityEligibilityStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.scout.activity_eligibility_status_updates",
                Tags.of("from_status", tagValue(fromStatus), "to_status", tagValue(toStatus))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }
}
