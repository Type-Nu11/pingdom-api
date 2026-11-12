package com.typenull.pingdom.identity.application.service.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserPurgeRetry;
import com.typenull.pingdom.identity.domain.UserPurgeRetry.Reason;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.*;
import com.typenull.pingdom.identity.infrastructure.persistence.UserPurgeReferenceRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawnUserPurgeTransactionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 0, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);
    @Mock private UserRepository users;
    @Mock private OAuthAccountRepository oauth;
    @Mock private PasswordResetTokenRepository tokens;
    @Mock private UserPurgeRetryRepository retries;
    @Mock private UserPurgeReferenceRepository references;
    @Mock private UserWithdrawalDataService withdrawal;
    @Mock private PrivacyProcessingOutboxPublisher publisher;
    private WithdrawnUserPurgeTransactionService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawnUserPurgeTransactionService(users, oauth, tokens, retries, references, withdrawal,
                new UserWithdrawalProperties(Duration.ofDays(30), 2, 2, Duration.ofDays(1)), publisher);
        when(references.tryAcquireLock()).thenReturn(true);
    }

    @Test
    void deletesAuthenticationDependenciesBeforeUserAndPublishesOnlyAfterFlush() {
        User user = expired();
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(user));

        assertThat(service.purge(1L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.DELETED);

        var order = inOrder(references, users, withdrawal, tokens, oauth, publisher);
        order.verify(references).tryAcquireLock();
        order.verify(users).findByIdForPurge(1L);
        order.verify(references).hasRetainedReferences(1L);
        order.verify(withdrawal).detachContentUserReferences(List.of(1L));
        order.verify(tokens).deleteAllByUserIds(List.of(1L));
        order.verify(oauth).deleteAllByUserIds(List.of(1L));
        order.verify(users).delete(user);
        order.verify(users).flush();
        order.verify(publisher).publish(any(PrivacyProcessingBulkEvent.class));
    }

    @Test
    void retainsAllDataAndRecordsBoundedReasonWhenBusinessReferenceRemains() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(expired()));
        when(references.hasRetainedReferences(1L)).thenReturn(true);

        assertThat(service.purge(1L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.DEFERRED);

        var saved = ArgumentCaptor.forClass(UserPurgeRetry.class);
        verify(retries).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.RETAINED_REFERENCE);
        assertThat(saved.getValue().getNextAttemptAt()).isEqualTo(NOW.plusDays(1));
        verify(users, never()).delete(any());
        verifyNoInteractions(tokens, oauth, withdrawal, publisher);
    }

    @Test
    void skipsBeforeCutoffAndDoesNotDeleteAnActiveUser() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(User.builder()
                .id(1L).status(UserStatus.WITHDRAWN).withdrawnAt(CUTOFF.plusNanos(1)).build()));
        when(users.findByIdForPurge(2L)).thenReturn(Optional.of(User.builder().id(2L).status(UserStatus.ACTIVE).build()));

        assertThat(service.purge(1L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);
        assertThat(service.purge(2L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);
        verify(references, never()).hasRetainedReferences(anyLong());
        verifyNoInteractions(tokens, oauth, withdrawal, publisher, retries);
    }

    @Test
    void skipsWhenAdvisoryLockIsBusy() {
        when(references.tryAcquireLock()).thenReturn(false);
        assertThat(service.purge(1L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);
        verifyNoInteractions(users, tokens, oauth, withdrawal, publisher, retries);
    }

    @Test
    void observesRetryDeadlineAgainInsideTheUserTransaction() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(expired()));
        when(retries.findById(1L)).thenReturn(Optional.of(
                UserPurgeRetry.defer(1L, Reason.PROCESSING_FAILED, NOW, NOW.plusMinutes(1))));
        assertThat(service.purge(1L, CUTOFF, NOW)).isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);
        verify(references, never()).hasRetainedReferences(anyLong());
        verifyNoInteractions(tokens, oauth, withdrawal, publisher);
    }

    @Test
    void failsClosedWhenSchemaInspectionFails() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(expired()));
        when(references.hasRetainedReferences(1L)).thenThrow(new IllegalStateException("metadata unavailable"));

        assertThatThrownBy(() -> service.purge(1L, CUTOFF, NOW)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tokens, oauth, withdrawal, publisher);
        verify(users, never()).delete(any());
    }

    @Test
    void propagatesAuditFailureToTheTransactionBoundary() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(expired()));
        doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(PrivacyProcessingBulkEvent.class));
        assertThatThrownBy(() -> service.purge(1L, CUTOFF, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsFailureOnlyForAStillExpiredUser() {
        when(users.findByIdForPurge(1L)).thenReturn(Optional.of(expired()));
        when(users.findByIdForPurge(2L)).thenReturn(Optional.empty());
        assertThat(service.recordFailure(1L, CUTOFF, NOW)).isTrue();
        assertThat(service.recordFailure(2L, CUTOFF, NOW)).isFalse();
        var saved = ArgumentCaptor.forClass(UserPurgeRetry.class);
        verify(retries).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo(Reason.PROCESSING_FAILED);
        verifyNoInteractions(tokens, oauth, withdrawal, publisher);
    }

    private User expired() {
        return User.builder().id(1L).status(UserStatus.WITHDRAWN).withdrawnAt(CUTOFF).build();
    }
}
