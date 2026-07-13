package com.typenull.pingdom.identity.application.service;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "travel.data-retention")
public record TravelDataRetentionProperties(
        Duration withdrawnUserRetention,
        @Min(1) Integer cleanupBatchSize
) {

    private static final Duration DEFAULT_WITHDRAWN_USER_RETENTION = Duration.ofDays(7);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    public TravelDataRetentionProperties {
        if (withdrawnUserRetention == null) {
            withdrawnUserRetention = DEFAULT_WITHDRAWN_USER_RETENTION;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
    }
}
