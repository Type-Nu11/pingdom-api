package com.typenull.pingdom.identity.application.service.withdrawal;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user.withdrawal")
public record UserWithdrawalProperties(
        Duration retention,
        @Min(1) Integer cleanupBatchSize
) {

    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    public UserWithdrawalProperties {
        if (retention == null) {
            retention = DEFAULT_RETENTION;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
    }
}
