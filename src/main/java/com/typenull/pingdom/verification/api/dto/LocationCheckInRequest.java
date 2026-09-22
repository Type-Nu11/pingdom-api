package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * 장소 ID와 위·경도(도), 위치 정확도(미터), 클라이언트 관측 시각을 받는다.
 * 좌표 범위와 필수 값은 Bean Validation이, 관측 신선도와 장소 반경은 서비스가 확인한다.
 */
public record LocationCheckInRequest(
        @NotNull Long placeId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") Double accuracyMeters,
        @NotNull Instant observedAt
) {}
