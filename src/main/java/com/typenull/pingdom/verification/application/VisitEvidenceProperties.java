package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "verification.visit-evidence")
public record VisitEvidenceProperties(Duration retention, @Min(1) Long maxFileSizeBytes,
        @Min(1) Integer cleanupBatchSize, @Min(1) Integer maxCleanupBatches) {
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_CLEANUP_BATCHES = 10;

    public VisitEvidenceProperties {
        retention = retention == null ? DEFAULT_RETENTION : retention;
        maxFileSizeBytes = maxFileSizeBytes == null ? DEFAULT_MAX_FILE_SIZE_BYTES : maxFileSizeBytes;
        cleanupBatchSize = cleanupBatchSize == null ? DEFAULT_CLEANUP_BATCH_SIZE : cleanupBatchSize;
        maxCleanupBatches = maxCleanupBatches == null ? DEFAULT_MAX_CLEANUP_BATCHES : maxCleanupBatches;
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("증빙 보관 기간은 0보다 커야 합니다.");
        }
    }
}
