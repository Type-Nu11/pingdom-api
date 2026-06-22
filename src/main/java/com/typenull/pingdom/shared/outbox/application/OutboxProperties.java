package com.typenull.pingdom.shared.outbox.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
        @NotNull Duration retention
) {
}
