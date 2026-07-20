package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class PlaceInformationMetrics {

    private final MeterRegistry meterRegistry;

    public PlaceInformationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEvidenceSubmitted(PlaceInformationSourceType sourceType) {
        meterRegistry.counter(
                "pingdom.place.information_evidence_submitted",
                Tags.of("source_type", tagValue(sourceType))
        ).increment();
    }

    public void recordVerificationStatusUpdate(
            PlaceInformationVerificationStatus fromStatus,
            PlaceInformationVerificationStatus toStatus
    ) {
        meterRegistry.counter(
                "pingdom.place.information_verification_status_updates",
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
