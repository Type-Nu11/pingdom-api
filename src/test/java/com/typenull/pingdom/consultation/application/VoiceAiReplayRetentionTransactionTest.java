package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiReplayRepository;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VoiceAiReplayRetentionTransactionTest {
    @Test
    void lateCompletionAfterParentDeletionDoesNotRecreateReplay() {
        var sessions = mock(VoiceAiSessionRepository.class);
        var replays = mock(VoiceAiReplayRepository.class);
        var transactions = new VoiceAiReplayTransactionService(sessions, replays);
        when(sessions.findByIdForUpdate("deleted")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transactions.complete("deleted", 1L, "request", "token", JsonNodeFactory.instance.objectNode()))
                .isInstanceOf(VoiceAiException.class).hasMessageContaining("세션");
        verifyNoInteractions(replays);
    }

    @Test
    void successfulCompletionUsesJvmClockWithoutMovingTimeOnReplay() {
        var sessions = mock(VoiceAiSessionRepository.class);
        var replays = mock(VoiceAiReplayRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime now = LocalDateTime.now(clock);
        var replay = VoiceAiReplay.start("s", "r", "hash", "token", now.minusMinutes(1), Duration.ofSeconds(30));
        when(sessions.findByIdForUpdate("s")).thenReturn(Optional.of(VoiceAiSession.create("s", 1L, now)));
        when(replays.findBySessionIdAndRequestId("s", "r")).thenReturn(Optional.of(replay));
        var transactions = new VoiceAiReplayTransactionService(sessions, replays, clock);
        transactions.complete("s", 1L, "r", "token", JsonNodeFactory.instance.objectNode());
        assertThat(replay.getCompletedAt()).isEqualTo(now);
    }
}
