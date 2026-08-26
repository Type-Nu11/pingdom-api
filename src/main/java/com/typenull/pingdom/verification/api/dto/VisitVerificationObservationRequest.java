package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record VisitVerificationObservationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotNull Instant observedAt
) {}
