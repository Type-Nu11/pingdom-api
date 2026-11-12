package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class VoiceAiRetentionPropertiesTest {
    @Test
    void defaultsAreBoundedAndRetentionIsFourteenDays() {
        var properties = new VoiceAiRetentionProperties(null, null, null);
        assertThat(properties.sessionBatchSize()).isEqualTo(10);
        assertThat(properties.replayBatchSize()).isEqualTo(100);
        assertThat(properties.maxBatchesPerRun()).isEqualTo(10);
        assertThat(VoiceAiRetentionProperties.RETENTION.toDays()).isEqualTo(14);
    }

    @Test
    void rejectsZeroAndExcessiveBatchLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new VoiceAiRetentionProperties(0, 1001, 101))).hasSize(3);
        }
    }
}
