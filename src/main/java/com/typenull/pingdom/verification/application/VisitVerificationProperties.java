package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 체류 인증의 기본값과 장소별 반경 예외를 애플리케이션 재배포 없이 조정합니다. */
@Validated
@ConfigurationProperties(prefix = "verification.visit-verification")
public record VisitVerificationProperties(
        @DecimalMin(value = "10.0") @DecimalMax("20.0") Double defaultRadiusMeters,
        Map<Long, @DecimalMin(value = "10.0") @DecimalMax("20.0") Double> radiusOverrides,
        @DecimalMin(value = "0.0", inclusive = false) Double maxAccuracyMeters,
        @NotNull Duration dwellDuration,
        @NotNull Duration sessionTtl,
        @NotNull Duration maxObservationGap,
        @NotNull Duration observationInterval,
        @NotNull Duration observationTtl,
        @NotNull Duration futureTolerance,
        @NotNull Duration retention,
        @DecimalMin(value = "10.0") @DecimalMax("10000.0") Double foregroundRadiusMeters,
        @NotNull Duration foregroundDwellDuration
) {
    public VisitVerificationProperties {
        if (defaultRadiusMeters == null) defaultRadiusMeters = 20.0;
        radiusOverrides = radiusOverrides == null ? Map.of() : Map.copyOf(radiusOverrides);
        if (maxAccuracyMeters == null) maxAccuracyMeters = defaultRadiusMeters;
        if (dwellDuration == null) dwellDuration = Duration.ofSeconds(30);
        if (sessionTtl == null) sessionTtl = Duration.ofMinutes(5);
        if (maxObservationGap == null) maxObservationGap = Duration.ofSeconds(15);
        if (observationInterval == null) observationInterval = Duration.ofSeconds(5);
        if (observationTtl == null) observationTtl = Duration.ofMinutes(1);
        if (futureTolerance == null) futureTolerance = Duration.ofSeconds(10);
        if (retention == null) retention = Duration.ofDays(30);
        if (foregroundRadiusMeters == null) foregroundRadiusMeters = 1000.0;
        if (foregroundDwellDuration == null) foregroundDwellDuration = Duration.ofSeconds(30);
        if (maxAccuracyMeters > defaultRadiusMeters) {
            throw new IllegalArgumentException("maxAccuracyMeters must not exceed defaultRadiusMeters");
        }
        if (dwellDuration.isZero() || dwellDuration.isNegative() || sessionTtl.isZero() || sessionTtl.isNegative()
                || maxObservationGap.isZero() || maxObservationGap.isNegative()
                || observationInterval.isZero() || observationInterval.isNegative()
                || observationTtl.isZero() || observationTtl.isNegative() || retention.isNegative()
                || futureTolerance.isNegative() || foregroundDwellDuration.isZero() || foregroundDwellDuration.isNegative()
                || observationInterval.compareTo(maxObservationGap) > 0
                || maxObservationGap.compareTo(sessionTtl) > 0) {
            throw new IllegalArgumentException("visit verification durations are invalid");
        }
    }

    public double radiusMetersFor(Long placeId) {
        return radiusOverrides.getOrDefault(placeId, defaultRadiusMeters);
    }
}
