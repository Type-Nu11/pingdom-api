package com.typenull.pingdom.shared.outbox.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 선점/정리 배치 크기, worker 동시성·큐 용량, 실패 한도와 시간 간격 설정이다.
 * Duration 값은 null만 검증하며 양수 및 상호 크기 관계를 이 record에서 별도로 검증하지 않는다.
 */
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
