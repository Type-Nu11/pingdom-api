package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "verification.location-check-in")
public record LocationCheckInProperties(
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1000.0") Double maxDistanceMeters,
        @DecimalMin(value = "0.0", inclusive = false) Double maxAccuracyMeters,
        @NotNull Duration observationTtl,
        @NotNull Duration futureTolerance
) {
    private static final double DEFAULT_MAX_DISTANCE_METERS = 100.0;
    private static final double DEFAULT_MAX_ACCURACY_METERS = 50.0;
    private static final Duration DEFAULT_OBSERVATION_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_FUTURE_TOLERANCE = Duration.ofSeconds(30);

    public LocationCheckInProperties {
        if (maxDistanceMeters == null) maxDistanceMeters = DEFAULT_MAX_DISTANCE_METERS;
        if (maxAccuracyMeters == null) maxAccuracyMeters = DEFAULT_MAX_ACCURACY_METERS;
        if (observationTtl == null) observationTtl = DEFAULT_OBSERVATION_TTL;
        if (futureTolerance == null) futureTolerance = DEFAULT_FUTURE_TOLERANCE;
        if (observationTtl.isZero() || observationTtl.isNegative()) {
            throw new IllegalArgumentException("observationTtl must be positive");
        }
        if (futureTolerance.isNegative()) {
            throw new IllegalArgumentException("futureTolerance must not be negative");
        }
    }
}
