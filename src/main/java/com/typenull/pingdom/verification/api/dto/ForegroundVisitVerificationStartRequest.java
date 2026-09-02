package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 장소 식별자 없이 현재 좌표로 서버가 방문 인증 장소를 판정하기 위한 요청입니다. */
public record ForegroundVisitVerificationStartRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotNull Instant observedAt
) {}
