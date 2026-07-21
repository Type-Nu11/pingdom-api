package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScoutFieldReportReviewRequest(
        @NotNull ScoutFieldReportStatus decision,
        @Size(max = 500) String reviewNote
) {}
