package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 체류 인증의 전역 기본값과 장소별 반경 예외를 서버 설정으로 관리. */
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
        @NotNull Duration retention,
        @DecimalMin(value = "10.0") @DecimalMax("10000.0") Double foregroundRadiusMeters,
        @NotNull Duration foregroundDwellDuration
) {
    /**
     * 장소별 반경을 불변 복사하고 누락된 관측·세션·보관 설정을 기본값으로 보완.
     * 정확도 허용치는 기본 반경을 넘을 수 없고 관측 간격 ≤ 최대 관측 공백 ≤ 세션 TTL이어야 함.
     * 미래 허용 오차와 보관 기간에는 0을 허용하지만 체류·관측 시간에는 양수를 요구.
     */
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

    /** 장소별 반경이 있으면 해당 미터 값을, 없으면 전역 기본 반경을 반환. */
    public double radiusMetersFor(Long placeId) {
        return radiusOverrides.getOrDefault(placeId, defaultRadiusMeters);
    }
}
