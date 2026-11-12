package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.consultation.application.VoiceAiRetentionService.CleanupResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class VoiceAiRetentionWorkerTest {
    private final VoiceAiRetentionService service = mock(VoiceAiRetentionService.class);

    @Test
    void stopsAtMaximumBatchesEvenIfEveryBatchHasProgress() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            when(service.cleanupBatch()).thenReturn(new CleanupResult(1, 2, 0));
            new VoiceAiRetentionWorker(service, new VoiceAiRetentionProperties(10, 100, 2), registry)
                    .cleanupExpiredSessions();
            verify(service, times(2)).cleanupBatch();
            assertThat(registry.get("pingdom.voice_ai_retention.deleted").tag("type", "replay").counter().count()).isEqualTo(4);
        } finally {
            registry.close();
        }
    }

    @Test
    void keepsCommittedBatchMetricsAndStopsWhenNextBatchFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            when(service.cleanupBatch()).thenReturn(new CleanupResult(0, 3, 0))
                    .thenThrow(new IllegalStateException("sensitive provider payload"));
            new VoiceAiRetentionWorker(service, new VoiceAiRetentionProperties(10, 100, 5), registry)
                    .cleanupExpiredSessions();
            verify(service, times(2)).cleanupBatch();
            assertThat(registry.get("pingdom.voice_ai_retention.deleted").tag("type", "replay").counter().count()).isEqualTo(3);
            assertThat(registry.get("pingdom.voice_ai_retention.cleanup").tag("result", "failure").counter().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    @Test
    void stopsOnNoProgressAndCountsLegacyInitializationSeparately() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            when(service.cleanupBatch()).thenReturn(new CleanupResult(0, 0, 2), new CleanupResult(0, 0, 0));
            new VoiceAiRetentionWorker(service, new VoiceAiRetentionProperties(10, 100, 5), registry)
                    .cleanupExpiredSessions();
            verify(service, times(2)).cleanupBatch();
            assertThat(registry.get("pingdom.voice_ai_retention.initialized").counter().count()).isEqualTo(2);
            assertThat(registry.get("pingdom.voice_ai_retention.deleted").tag("type", "replay").counter().count()).isZero();
        } finally {
            registry.close();
        }
    }
}
