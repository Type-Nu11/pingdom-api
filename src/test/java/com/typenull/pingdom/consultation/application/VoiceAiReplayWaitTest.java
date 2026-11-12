package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VoiceAiReplayWaitTest {
    private final AtomicLong nanos = new AtomicLong();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void increasesPollingIntervalWithinOneSecond() {
        List<Long> pauses = new ArrayList<>();
        VoiceAiReplayWait wait = new VoiceAiReplayWait(nanos::get, duration -> {
            pauses.add(duration);
            nanos.addAndGet(duration);
        });

        for (int attempt = 0; attempt < 6; attempt++) {
            wait.beforeAttempt();
            wait.pause();
        }

        assertThat(pauses).containsExactly(
                Duration.ofMillis(100).toNanos(), Duration.ofMillis(200).toNanos(),
                Duration.ofMillis(400).toNanos(), Duration.ofMillis(800).toNanos(),
                Duration.ofSeconds(1).toNanos(), Duration.ofSeconds(1).toNanos());
    }

    @Test
    void doesNotPollEarlyAfterSpuriousWakeup() {
        List<Long> pauses = new ArrayList<>();
        VoiceAiReplayWait wait = new VoiceAiReplayWait(nanos::get, duration -> {
            pauses.add(duration);
            nanos.addAndGet(Math.min(duration, Duration.ofMillis(50).toNanos()));
        });

        wait.pause();

        assertThat(pauses).containsExactly(Duration.ofMillis(100).toNanos(), Duration.ofMillis(50).toNanos());
        assertThat(nanos.get()).isEqualTo(Duration.ofMillis(100).toNanos());
    }

    @Test
    void stopsBeforeStartingDatabaseAttemptWithoutEnoughBudget() {
        VoiceAiReplayWait wait = new VoiceAiReplayWait(nanos::get, nanos::addAndGet);
        nanos.set(Duration.ofMillis(29_500).toNanos());

        assertUnavailable(wait::beforeAttempt);
    }

    @Test
    void preservesInterruptBeforeOrDuringWaiting() {
        VoiceAiReplayWait wait = new VoiceAiReplayWait(nanos::get, duration -> Thread.currentThread().interrupt());

        assertUnavailable(wait::pause);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertUnavailable(wait::beforeAttempt);
    }

    private void assertUnavailable(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(VoiceAiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(VoiceAiErrorCode.PROVIDER_UNAVAILABLE));
    }
}
