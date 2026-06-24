package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final MeterRegistry meterRegistry;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuthFailure(AuthErrorCode errorCode, String source) {
        meterRegistry.counter(
                "pingdom.auth.failures",
                Tags.of(
                        "code", tagValue(errorCode),
                        "source", safeTag(source),
                        "status", errorCode == null ? "unknown" : String.valueOf(errorCode.getStatus().value())
                )
        ).increment();
    }

    public void recordRefreshTokenSuccess() {
        meterRegistry.counter(
                "pingdom.auth.refresh_token",
                Tags.of("result", "success", "reason", "none")
        ).increment();
    }

    public void recordRefreshTokenFailure(String reason) {
        meterRegistry.counter(
                "pingdom.auth.refresh_token",
                Tags.of("result", "failure", "reason", safeTag(reason))
        ).increment();
    }

    private String tagValue(Enum<?> value) {
        return value == null ? "unknown" : value.name();
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
