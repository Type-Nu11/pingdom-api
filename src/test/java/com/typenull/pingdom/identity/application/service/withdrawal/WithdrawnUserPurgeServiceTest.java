package com.typenull.pingdom.identity.application.service.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository.PurgeCandidate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WithdrawnUserPurgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 0, 0);

    @Test
    void continuesAfterFailureAndRetentionAndCountsOnlyCommittedDeletes() {
        UserRepository users = mock(UserRepository.class);
        var transactions = mock(WithdrawnUserPurgeTransactionService.class);
        var service = new WithdrawnUserPurgeService(users,
                new UserWithdrawalProperties(Duration.ofDays(30), 3, 2, Duration.ofDays(1)), transactions);
        when(users.findPurgeCandidates(any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(List.of(candidate(1), candidate(2), candidate(3)), List.of());
        when(transactions.purge(eq(1L), any(), eq(NOW))).thenThrow(new IllegalStateException("failure"));
        when(transactions.purge(eq(2L), any(), eq(NOW))).thenReturn(WithdrawnUserPurgeTransactionService.Result.DEFERRED);
        when(transactions.purge(eq(3L), any(), eq(NOW))).thenReturn(WithdrawnUserPurgeTransactionService.Result.DELETED);
        when(transactions.recordFailure(eq(1L), any(), eq(NOW))).thenThrow(new IllegalStateException("retry unavailable"));

        assertThat(service.purgeExpiredUsers(NOW)).isEqualTo(1);

        verify(transactions).recordFailure(eq(1L), any(), eq(NOW));
        verify(users).findPurgeCandidates(any(), any(), eq(NOW), any(),
                eq(LocalDateTime.of(1, 1, 1, 0, 0)), eq(NOW.minusDays(31)), eq(3L), any());
    }

    @Test
    void stopsAtConfiguredBatchLimitEvenWhenMoreCandidatesRemain() {
        UserRepository users = mock(UserRepository.class);
        var transactions = mock(WithdrawnUserPurgeTransactionService.class);
        var service = new WithdrawnUserPurgeService(users,
                new UserWithdrawalProperties(Duration.ofDays(30), 1, 1, Duration.ofDays(1)), transactions);
        when(users.findPurgeCandidates(any(), any(), any(), any(), any(), any(), anyLong(), any())).thenReturn(List.of(candidate(1)));
        when(transactions.purge(eq(1L), any(), eq(NOW))).thenReturn(WithdrawnUserPurgeTransactionService.Result.SKIPPED);

        assertThat(service.purgeExpiredUsers(NOW)).isZero();
        verify(users, times(1)).findPurgeCandidates(any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    private PurgeCandidate candidate(long id) {
        return new PurgeCandidate() {
            public Long getUserId() { return id; }
            public LocalDateTime getWithdrawnAt() { return NOW.minusDays(31); }
            public LocalDateTime getAttemptedAt() { return LocalDateTime.of(1, 1, 1, 0, 0); }
        };
    }
}
