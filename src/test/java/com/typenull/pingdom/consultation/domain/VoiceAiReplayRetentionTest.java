package com.typenull.pingdom.consultation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VoiceAiReplayRetentionTest {
    private final LocalDateTime started = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Test
    void storesClaimLeaseAndCompletionTimeOnlyForCurrentToken() {
        var replay = VoiceAiReplay.start("session", "request", "hash", "old", started, Duration.ofSeconds(30));
        replay.takeOverProcessing("current", started.plusMinutes(1), Duration.ofSeconds(60));
        assertThat(replay.getProcessingLeaseUntil()).isEqualTo(started.plusMinutes(2));
        assertThat(replay.complete("old", JsonNodeFactory.instance.objectNode(), started.plusMinutes(3))).isFalse();
        assertThat(replay.getCompletedAt()).isNull();
        assertThat(replay.complete("current", JsonNodeFactory.instance.objectNode(), started.plusMinutes(4))).isTrue();
        assertThat(replay.getCompletedAt()).isEqualTo(started.plusMinutes(4));
        assertThat(replay.getProcessingLeaseUntil()).isNull();
        assertThat(replay.complete("current", JsonNodeFactory.instance.objectNode(), started.plusDays(1))).isFalse();
        assertThat(replay.getCompletedAt()).isEqualTo(started.plusMinutes(4));
    }
}
