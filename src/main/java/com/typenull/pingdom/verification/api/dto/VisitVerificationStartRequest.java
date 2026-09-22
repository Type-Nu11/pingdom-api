package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * 지정 장소의 체류 인증 시작에 필요한 좌표(도)·정확도(미터)·관측 시각이다.
 * 기본 Bean Validation 외의 반경 및 유효 시간 검사는 서비스에서 수행한다.
 */
public record VisitVerificationStartRequest(
        @NotNull Long placeId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotNull Instant observedAt
) {}
