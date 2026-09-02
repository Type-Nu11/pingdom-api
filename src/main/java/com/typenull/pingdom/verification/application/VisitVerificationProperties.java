package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 체류 인증의 전역 기본값과 장소별 반경 예외를 서버 설정으로 관리합니다. */
@Validated
@ConfigurationProperties(prefix = "verification.visit-verification")
public record VisitVerificationProperties(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double defaultRadiusMeters,
        Map<Long, @DecimalMin(value = "0.0", inclusive = false) Double> radiusOverrides,
        @DecimalMin(value = "0.0", inclusive = false) Double maxAccuracyMeters,
        @NotNull Duration dwellDuration,
        @NotNull Duration sessionTtl,
        @NotNull Duration maxObservationGap,
        @NotNull Duration observationInterval,
        @NotNull Duration observationTtl,
        @NotNull Duration futureTolerance,
        @NotNull Duration retention
) {
    public VisitVerificationProperties {
        Objects.requireNonNull(defaultRadiusMeters, "defaultRadiusMeters must not be null");
        radiusOverrides = radiusOverrides == null ? Map.of() : Map.copyOf(radiusOverrides);
        if (maxAccuracyMeters == null) maxAccuracyMeters = defaultRadiusMeters;
        Objects.requireNonNull(dwellDuration, "dwellDuration must not be null");
        if (sessionTtl == null) sessionTtl = Duration.ofMinutes(5);
        if (maxObservationGap == null) maxObservationGap = Duration.ofSeconds(15);
        if (observationInterval == null) observationInterval = Duration.ofSeconds(5);
        if (observationTtl == null) observationTtl = Duration.ofMinutes(1);
        if (futureTolerance == null) futureTolerance = Duration.ofSeconds(10);
        if (retention == null) retention = Duration.ofDays(30);
        if (maxAccuracyMeters > defaultRadiusMeters) {
            throw new IllegalArgumentException("maxAccuracyMeters must not exceed defaultRadiusMeters");
        }
        if (dwellDuration.isZero() || dwellDuration.isNegative() || sessionTtl.isZero() || sessionTtl.isNegative()
                || maxObservationGap.isZero() || maxObservationGap.isNegative()
                || observationInterval.isZero() || observationInterval.isNegative()
                || observationTtl.isZero() || observationTtl.isNegative() || retention.isNegative()
                || futureTolerance.isNegative() || observationInterval.compareTo(maxObservationGap) > 0
                || maxObservationGap.compareTo(sessionTtl) > 0) {
            throw new IllegalArgumentException("visit verification durations are invalid");
        }
    }

    public double radiusMetersFor(Long placeId) {
        return radiusOverrides.getOrDefault(placeId, defaultRadiusMeters);
    }
}
