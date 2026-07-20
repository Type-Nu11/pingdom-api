package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VisitorVerificationReportReviewRequest(
        @NotNull VisitorVerificationReportStatus decision,
        @Size(max = 500) String reviewNote
) {}
