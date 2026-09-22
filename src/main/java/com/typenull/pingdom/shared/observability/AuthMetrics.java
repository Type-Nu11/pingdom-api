package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * 인증 실패의 코드·HTTP 상태·발생 위치와 refresh 성공·실패를 카운터로 기록.
 * 문자열 태그는 공백만 unknown으로 바꾸므로 호출자는 제한된 값만 전달해야 함.
 */
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
