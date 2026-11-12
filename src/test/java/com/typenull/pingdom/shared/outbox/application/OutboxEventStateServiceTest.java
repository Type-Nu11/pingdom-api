package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 0, 0);
    private static final OutboxClaim CLAIM = new OutboxClaim("event-id", "original-token");
    @Mock private OutboxEventRepository repository;
    private OutboxEventStateService service;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(20, 2, 20, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        service = new OutboxEventStateService(repository, properties, new OutboxBackoffPolicy(properties),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void successRequiresOriginalTokenAndAffectedRow() {
        owned();
        when(repository.completeClaim(CLAIM.eventId(), CLAIM.claimToken())).thenReturn(0, 1);

        assertThat(service.markSucceeded(CLAIM)).isFalse();
        assertThat(service.markSucceeded(CLAIM)).isTrue();
        verify(repository, never()).findById(any());
    }

    @Test
    void rejectedFailureDoesNotReportRetry() {
        owned();
        when(repository.failClaim(eq(CLAIM.eventId()), eq(CLAIM.claimToken()), eq(5), eq(10000L), anyString()))
                .thenReturn(0);

        assertThat(service.markFailed(CLAIM, new IllegalStateException("late"))).isNull();
    }

    @Test
    void failureUsesAttemptBackoffAndReturnsOnlyAppliedState() {
        OutboxEvent event = owned();
        event.fail(NOW.minusMinutes(1), 5, NOW, "first");
        event.claim(CLAIM.claimToken(), NOW, NOW.plusMinutes(5));
        when(repository.failClaim(eq(CLAIM.eventId()), eq(CLAIM.claimToken()), eq(5), eq(20000L), anyString()))
                .thenReturn(1);

        assertThat(service.markFailed(CLAIM, new IllegalStateException("temporary")))
                .isEqualTo(OutboxEventStatus.RETRY);
    }

    @Test
    void expiredOrReplacedClaimCannotFailOrLoadPayload() {
        when(repository.findOwnedProcessingEvent(CLAIM.eventId(), CLAIM.claimToken())).thenReturn(Optional.empty());

        assertThat(service.findProcessingEvent(CLAIM)).isNull();
        assertThat(service.markFailed(CLAIM, new IllegalStateException("late"))).isNull();
        verify(repository, never()).failClaim(any(), any(), anyInt(), anyLong(), any());
    }

    @Test
    void leaseIsRecheckedAgainstDatabaseTimeAfterLockAcquisition() {
        owned();
        when(repository.databaseNow()).thenReturn(NOW.plusMinutes(5));

        assertThat(service.markSucceeded(CLAIM)).isFalse();
        assertThat(service.renewClaim(CLAIM)).isFalse();
        verify(repository, never()).completeClaim(any(), any());
        verify(repository, never()).renewClaim(any(), any(), anyLong(), anyLong());
    }

    @Test
    void heartbeatAndReleaseKeepOriginalTokenAndReturnAffectedRows() {
        owned();
        when(repository.renewClaim(CLAIM.eventId(), CLAIM.claimToken(), 300000L, 1800000L)).thenReturn(0, 1);
        when(repository.releaseClaim(CLAIM.eventId(), CLAIM.claimToken())).thenReturn(0);

        assertThat(service.renewClaim(CLAIM)).isFalse();
        assertThat(service.renewClaim(CLAIM)).isTrue();
        assertThat(service.releaseClaim(CLAIM)).isFalse();
    }

    @Test
    void manualRetryClearsOwnershipAndResetsAttempts() {
        OutboxEvent event = event();
        event.claim("failed-token", NOW.minusSeconds(1), NOW.plusMinutes(5));
        event.fail(NOW, 1, NOW, "failed");
        when(repository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        var result = service.retryFailedEvent(event.getEventId());

        assertThat(result.before().status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(result.after().status()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getLeaseUntil()).isNull();
    }

    @Test
    void pendingEventCannotBeManuallyRetried() {
        OutboxEvent event = event();
        when(repository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        assertThat(service.retryFailedEvent(event.getEventId()).outcome())
                .isEqualTo(OutboxEventStateService.ManualRetryOutcome.NOT_RETRYABLE);
        verify(repository, never()).databaseNow();
    }

    private OutboxEvent owned() {
        OutboxEvent event = event();
        event.claim(CLAIM.claimToken(), NOW.minusSeconds(1), NOW.plusMinutes(5));
        when(repository.findByEventIdForUpdate(CLAIM.eventId())).thenReturn(Optional.of(event));
        when(repository.databaseNow()).thenReturn(NOW);
        return event;
    }

    private OutboxEvent event() {
        return OutboxEvent.create("email:1", OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}", "USER", "1", NOW.minusMinutes(10));
    }
}
