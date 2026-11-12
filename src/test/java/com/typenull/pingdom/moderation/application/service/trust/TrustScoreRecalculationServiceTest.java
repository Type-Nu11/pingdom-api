package com.typenull.pingdom.moderation.application.service.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeReason;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrustScoreRecalculationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ReporterModerationPolicyRepository policies;
    @Mock
    private TrustScoreChangeHistoryRepository histories;

    private TrustScoreRecalculationService service;

    @BeforeEach
    void setUp() {
        service = new TrustScoreRecalculationService(policies, histories, CLOCK);
    }

    @Test
    void usesLockedStatisticsAndPreservesCountersAndRestriction() {
        ReporterModerationPolicy policy = policy(20);
        when(policies.findByReporterUserIdForUpdate(7L)).thenReturn(Optional.of(policy));

        var result = service.recalculate(7L);

        assertThat(result.processed()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(policy.getTrustScore()).isEqualTo(45);
        assertThat(policy.getSubmittedCount()).isEqualTo(5);
        assertThat(policy.getAcceptedCount()).isEqualTo(1);
        assertThat(policy.getFalseReportCount()).isEqualTo(3);
        assertThat(policy.getRestrictedUntil()).isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));
        ArgumentCaptor<TrustScoreChangeHistory> history = ArgumentCaptor.forClass(TrustScoreChangeHistory.class);
        verify(histories).save(history.capture());
        assertThat(history.getValue().getBeforeScore()).isEqualTo(20);
        assertThat(history.getValue().getAfterScore()).isEqualTo(45);
        assertThat(history.getValue().getReason()).isEqualTo(TrustScoreChangeReason.BATCH_RECALCULATION);
        assertThat(history.getValue().getChangedAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void unchangedScoreDoesNotAppendHistoryOnRerun() {
        when(policies.findByReporterUserIdForUpdate(7L)).thenReturn(Optional.of(policy(45)));

        var result = service.recalculate(7L);

        assertThat(result.processed()).isTrue();
        assertThat(result.changed()).isFalse();
        verifyNoInteractions(histories);
    }

    @Test
    void skipsPolicyDeletedAfterIdScan() {
        when(policies.findByReporterUserIdForUpdate(7L)).thenReturn(Optional.empty());

        var result = service.recalculate(7L);

        assertThat(result.processed()).isFalse();
        assertThat(result.changed()).isFalse();
        verifyNoInteractions(histories);
    }

    @Test
    void propagatesHistoryFailureToRollbackPolicyTransaction() {
        when(policies.findByReporterUserIdForUpdate(7L)).thenReturn(Optional.of(policy(20)));
        RuntimeException failure = new IllegalStateException("history unavailable");
        when(histories.save(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

        assertThatThrownBy(() -> service.recalculate(7L)).isSameAs(failure);
    }

    private ReporterModerationPolicy policy(int score) {
        return ReporterModerationPolicy.builder().reporterUserId(7L).reporterUsername("reporter")
                .submittedCount(5).acceptedCount(1).declinedCount(3).falseReportCount(3)
                .trustScore(score).restrictedUntil(LocalDateTime.of(2026, 11, 20, 0, 0))
                .restrictionReason("existing restriction").build();
    }
}
