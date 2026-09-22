package com.typenull.pingdom.verification.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 증빙 보관 및 정리 배치 설정.
 * retention 기본값은 30일, maxFileSizeBytes는 10MiB이며 업로드 입력과 재인코딩 결과 모두에 적용.
 * 정리는 기본 10건씩 최대 10회이며 배치 크기 상한 10은 Bean Validation에서 적용.
 */
@Validated
@ConfigurationProperties(prefix = "verification.visit-evidence")
public record VisitEvidenceProperties(Duration retention, @Min(1) Long maxFileSizeBytes,
        @Min(1) @Max(MAX_CLEANUP_BATCH_SIZE) Integer cleanupBatchSize, @Min(1) Integer maxCleanupBatches) {
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    public static final int MAX_CLEANUP_BATCH_SIZE = 10;
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = MAX_CLEANUP_BATCH_SIZE;
    private static final int DEFAULT_MAX_CLEANUP_BATCHES = 10;

    /** 누락된 설정을 기본값으로 채우고 양수가 아닌 보관 기간을 즉시 거부. */
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
