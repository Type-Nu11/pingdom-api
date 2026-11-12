package com.typenull.pingdom.identity.application.service.withdrawal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 기존 보존 기간과 삭제 배치 상한을 보관. 실패 재시도는 기존 cleanup-delay와 같은 주기를 사용.
 */
@Validated
@ConfigurationProperties(prefix = "user.withdrawal")
public record UserWithdrawalProperties(
        Duration retention,
        @Min(1) Integer cleanupBatchSize,
        @Min(1) Integer maxCleanupBatches,
        Duration cleanupDelay
) {

    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_CLEANUP_BATCHES = 10;
    private static final Duration DEFAULT_CLEANUP_DELAY = Duration.ofDays(1);

    public UserWithdrawalProperties {
        if (retention == null) {
            retention = DEFAULT_RETENTION;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
        if (maxCleanupBatches == null) {
            maxCleanupBatches = DEFAULT_MAX_CLEANUP_BATCHES;
        }
        if (cleanupDelay == null) {
            cleanupDelay = DEFAULT_CLEANUP_DELAY;
        }
    }

    @AssertTrue(message = "탈퇴 보존기간과 삭제 재시도 주기는 양수여야 합니다.")
    public boolean isDurationValid() {
        return !retention.isNegative() && !retention.isZero()
                && !cleanupDelay.isNegative() && !cleanupDelay.isZero();
    }
}
