package com.typenull.pingdom.shared.outbox.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        @Min(1) int batchSize,
        @Min(1) int workerConcurrency,
        @Min(0) int workerQueueCapacity,
        @Min(1) int cleanupBatchSize,
        @Min(1) int maxAttempts,
        @NotNull Duration baseBackoff,
        @NotNull Duration maxBackoff,
        @NotNull Duration processingTimeout,
        @NotNull Duration retention,
        @DefaultValue("PT30S") @NotNull Duration heartbeatInterval,
        @DefaultValue("PT30M") @NotNull Duration maxExecutionTime
) {
    @ConstructorBinding
    public OutboxProperties {
    }

    /** 기존 명시적 생성자 호출도 운영 기본 heartbeat 계약을 사용한다. */
    public OutboxProperties(int batchSize, int workerConcurrency, int workerQueueCapacity,
            int cleanupBatchSize, int maxAttempts, Duration baseBackoff, Duration maxBackoff,
            Duration processingTimeout, Duration retention) {
        this(batchSize, workerConcurrency, workerQueueCapacity, cleanupBatchSize, maxAttempts,
                baseBackoff, maxBackoff, processingTimeout, retention,
                Duration.ofSeconds(30), Duration.ofMinutes(30));
    }

    @AssertTrue(message = "Outbox 기간은 양수이며 heartbeat < lease <= 최대 실행 시간이어야 합니다.")
    public boolean isTimingValid() {
        return positive(baseBackoff) && positive(maxBackoff) && positive(processingTimeout)
                && positive(retention) && positive(heartbeatInterval) && positive(maxExecutionTime)
                && baseBackoff.compareTo(maxBackoff) <= 0
                && heartbeatInterval.compareTo(processingTimeout) < 0
                && processingTimeout.compareTo(maxExecutionTime) <= 0;
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative() && value.toMillis() > 0;
    }
}
