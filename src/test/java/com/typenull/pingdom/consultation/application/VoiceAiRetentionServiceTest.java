package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiRetentionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceAiRetentionServiceTest {
    private final VoiceAiRetentionRepository repository = mock(VoiceAiRetentionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final LocalDateTime cutoff = now.minusDays(14);
    private final GeminiProperties gemini = mock(GeminiProperties.class);

    @Test
    void sharesOneReplayBudgetAcrossSessionsAndDoesNotCascadeRemainingChildren() {
        var service = service(3);
        when(repository.lockEligibleSessions(cutoff, 10)).thenReturn(List.of("first", "second", "third"));
        when(repository.deleteReplayBatch("first", cutoff, 3)).thenReturn(2);
        when(repository.deleteReplayBatch("second", cutoff, 1)).thenReturn(1);
        when(repository.deleteEmptySession("second", cutoff)).thenReturn(1);

        var result = service.cleanupBatch();

        assertThat(result.deletedReplays()).isEqualTo(3);
        assertThat(result.deletedSessions()).isEqualTo(1);
        verify(repository).deleteEmptySession("first", cutoff);
        verify(repository, never()).initializeLegacyTimestamps(eq("third"), any(), any(), anyInt());
        verify(repository, never()).deleteEmptySession("third", cutoff);
    }

    @Test
    void legacyInitializationConsumesBudgetAndUsesOneJvmLocalObservation() {
        var service = service(2);
        when(repository.lockEligibleSessions(cutoff, 10)).thenReturn(List.of("legacy", "next"));
        when(repository.initializeLegacyTimestamps("legacy", now, now.plusSeconds(30), 2)).thenReturn(2);

        var result = service.cleanupBatch();

        assertThat(result.initializedTimestamps()).isEqualTo(2);
        assertThat(result.deletedReplays()).isZero();
        assertThat(result.hasProgress()).isTrue();
        verify(repository, never()).deleteReplayBatch(anyString(), any(), anyInt());
        verify(repository, never()).initializeLegacyTimestamps(eq("next"), any(), any(), anyInt());
    }

    @Test
    void returnsNoProgressForAnEmptyCandidateBatch() {
        when(repository.lockEligibleSessions(cutoff, 10)).thenReturn(List.of());
        var result = service(100).cleanupBatch();
        assertThat(result.hasProgress()).isFalse();
        verify(repository, never()).deleteEmptySession(anyString(), any());
    }

    private VoiceAiRetentionService service(int rows) {
        when(gemini.connectTimeout()).thenReturn(Duration.ofSeconds(2));
        when(gemini.readTimeout()).thenReturn(Duration.ofSeconds(5));
        return new VoiceAiRetentionService(repository, new VoiceAiRetentionProperties(10, rows, 3), gemini, clock);
    }
}
