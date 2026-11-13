package com.typenull.pingdom.consultation.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "voice-ai.retention")
public record VoiceAiRetentionProperties(
        @Min(1) @Max(100) Integer sessionBatchSize,
        @Min(1) @Max(1000) Integer replayBatchSize,
        @Min(1) @Max(100) Integer maxBatchesPerRun
) {
    static final Duration RETENTION = Duration.ofDays(14);

    public VoiceAiRetentionProperties {
        if (sessionBatchSize == null) sessionBatchSize = 10;
        if (replayBatchSize == null) replayBatchSize = 100;
        if (maxBatchesPerRun == null) maxBatchesPerRun = 10;
    }
}
