package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

/** 진행 세션의 후속 위치 관측. 좌표는 도, 정확도는 미터이며 관측 시각과 서버 수신 시각은 별개. */
public record VisitVerificationObservationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotNull Instant observedAt
) {}
