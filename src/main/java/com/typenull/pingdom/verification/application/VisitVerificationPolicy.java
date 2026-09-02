package com.typenull.pingdom.verification.application;

import java.time.Duration;
import java.util.Objects;

/** 새 체류 인증 세션에 확정할 반경과 체류 시간 정책입니다. */
public record VisitVerificationPolicy(double requiredRadiusMeters, Duration requiredDwellDuration) {
    public VisitVerificationPolicy {
        if (requiredRadiusMeters <= 0) {
            throw new IllegalArgumentException("requiredRadiusMeters must be positive");
        }
        requiredDwellDuration = Objects.requireNonNull(requiredDwellDuration, "requiredDwellDuration must not be null");
        if (requiredDwellDuration.isZero() || requiredDwellDuration.isNegative()) {
            throw new IllegalArgumentException("requiredDwellDuration must be positive");
        }
    }
}
