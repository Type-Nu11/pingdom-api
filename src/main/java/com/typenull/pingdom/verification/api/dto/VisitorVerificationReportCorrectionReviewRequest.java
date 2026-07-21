package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VisitorVerificationReportCorrectionReviewRequest(
        @NotNull Decision decision,
        @Size(max = 500) String reviewNote
) {
    public enum Decision {
        ACCEPTED,
        REJECTED;

        public VisitorVerificationReportCorrectionStatus toStatus() {
            return VisitorVerificationReportCorrectionStatus.valueOf(name());
        }
    }
}
