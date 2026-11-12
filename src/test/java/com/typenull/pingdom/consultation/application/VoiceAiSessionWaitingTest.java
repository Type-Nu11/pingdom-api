package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.sql.SQLException;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

class VoiceAiSessionWaitingTest {
    private final VoiceAiReplayTransactionService transactions = mock(VoiceAiReplayTransactionService.class);
    private final GeminiVoiceClient provider = mock(GeminiVoiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final VoiceAiSessionService service = new VoiceAiSessionService(
            mock(VoiceAiSessionRepository.class), new GeminiProperties(true, "test-key", null, null, null, null),
            provider, new ProviderEnvelopeValidator(), transactions, Clock.systemUTC(),
            () -> new VoiceAiReplayWait(nanos::get, nanos::addAndGet));

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void boundsAllCompletionWaitsWithoutReleasingOtherOwner(Operation operation) {
        if (operation == Operation.SEND) {
            when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                    .thenReturn(VoiceAiReplayClaim.processing());
        }

        assertUnavailable(() -> invoke(operation));

        assertThat(nanos.get()).isLessThanOrEqualTo(Duration.ofSeconds(30).toNanos());
        verifyNoInteractions(provider);
        verify(transactions, never()).release(anyString(), anyLong(), anyString(), anyString());
        verify(transactions, never()).complete(anyString(), anyLong(), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void interruptedWaiterDoesNotStartDatabaseWork(Operation operation) {
        Thread.currentThread().interrupt();

        assertUnavailable(() -> invoke(operation));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(transactions, provider);
    }

    @ParameterizedTest
    @MethodSource("databaseTimeouts")
    void retriesRolledBackDatabaseTimeoutOutsideTransaction(RuntimeException timeout) throws Exception {
        JsonNode result = envelope();
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenThrow(timeout)
                .thenReturn(VoiceAiReplayClaim.completed(result));

        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(result);

        assertThat(nanos.get()).isPositive();
        verifyNoInteractions(provider);
        verify(transactions, times(2)).claim(anyString(), anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void propagatesUnrelatedDatabaseFailureWithoutRetrying() {
        RuntimeException failure = new DataAccessResourceFailureException("connection failed");
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.send("session", 1L, "hello", "r1")).isSameAs(failure);

        verify(transactions, times(1)).claim(anyString(), anyLong(), anyString(), anyString(), any(), any());
        assertThat(nanos.get()).isZero();
    }

    private static Stream<RuntimeException> databaseTimeouts() {
        return Stream.of(
                new QueryTimeoutException("lock wait"),
                new DataAccessResourceFailureException("wrapped", new SQLException("query canceled", "57014")),
                new DataAccessResourceFailureException("wrapped", new SQLException("lock timeout", "55P03")),
                new org.springframework.orm.jpa.JpaSystemException(
                        new org.hibernate.TransactionException("transaction timeout expired")));
    }

    @Test
    void ownerPersistsResultEvenWhenProviderOutlastsWaitBudget() throws Exception {
        JsonNode result = envelope();
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(VoiceAiReplayClaim.owner("owner"));
        when(provider.generateEnvelope("hello", "r1")).thenAnswer(invocation -> {
            nanos.addAndGet(Duration.ofSeconds(60).toNanos());
            return result;
        });
        when(transactions.complete("session", 1L, "r1", "owner", result)).thenReturn(result);

        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(result);

        verify(transactions, never()).release(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void interruptionAfterClaimReleasesOnlyOwnTokenBeforeProviderStarts() {
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return VoiceAiReplayClaim.owner("mine");
                });

        assertUnavailable(() -> service.send("session", 1L, "hello", "r1"));

        verify(transactions).abandonUnstarted("session", 1L, "r1", "mine");
        verify(transactions, never()).release(anyString(), anyLong(), anyString(), anyString());
        verifyNoInteractions(provider);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void cleanupFailureDoesNotMaskInterruptedWaitOutcome() {
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return VoiceAiReplayClaim.owner("mine");
                });
        doThrow(new QueryTimeoutException("cleanup lock timeout")).when(transactions)
                .abandonUnstarted("session", 1L, "r1", "mine");

        assertUnavailable(() -> service.send("session", 1L, "hello", "r1"));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void boundsRewaitingAfterLosingProcessingToken() throws Exception {
        JsonNode result = envelope();
        when(transactions.claim(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(VoiceAiReplayClaim.owner("old"), VoiceAiReplayClaim.processing());
        when(provider.generateEnvelope("hello", "r1")).thenReturn(result);

        assertUnavailable(() -> service.send("session", 1L, "hello", "r1"));

        verify(provider, times(1)).generateEnvelope("hello", "r1");
        verify(transactions, never()).release(anyString(), anyLong(), anyString(), anyString());
    }

    private void invoke(Operation operation) {
        switch (operation) {
            case SEND -> service.send("session", 1L, "hello", "r1");
            case REFRESH -> service.refresh("session", 1L);
            case CLOSE -> service.close("session", 1L);
        }
    }

    private JsonNode envelope() throws Exception {
        return new ObjectMapper().readTree("""
                {"schemaVersion":1,"id":"r1","kind":"assistant_message","text":"안내"}
                """);
    }

    private void assertUnavailable(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(VoiceAiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(VoiceAiErrorCode.PROVIDER_UNAVAILABLE));
    }

    private enum Operation { SEND, REFRESH, CLOSE }
}
