package com.typenull.pingdom.privacy.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "privacy.processing-history")
public record PrivacyProcessingHistoryProperties(
        @Min(1) Integer retentionMonths,
        @Min(1) Integer cleanupBatchSize
) {

    private static final int DEFAULT_RETENTION_MONTHS = 3;
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 100;

    public PrivacyProcessingHistoryProperties {
        if (retentionMonths == null) {
            retentionMonths = DEFAULT_RETENTION_MONTHS;
        }
        if (cleanupBatchSize == null) {
            cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;
        }
    }
}
