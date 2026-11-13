package com.typenull.pingdom.moderation.application.service.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TrustScoreBatchServiceTest {

    @Mock
    private ReporterModerationPolicyRepository policies;
    @Mock
    private TrustScoreChangeHistoryRepository histories;
    @Mock
    private TrustScoreRecalculationService recalculation;

    private TrustScoreBatchService service;

    @BeforeEach
    void setUp() {
        service = new TrustScoreBatchService(policies, histories, recalculation);
    }

    @Test
    void returnsZeroWhenNoPolicyExists() {
        when(policies.findMaxReporterUserId()).thenReturn(null);

        assertThat(service.recalculate().processedCount()).isZero();
        verifyNoInteractions(recalculation);
    }

    @Test
    void boundsIdPagesAndCountsOnlyCommittedExistingPolicies() {
        PageRequest page = PageRequest.of(0, 100);
        when(policies.findMaxReporterUserId()).thenReturn(9L);
        when(policies.findReporterUserIdsAfter(null, 9L, page)).thenReturn(List.of(2L, 7L));
        when(policies.findReporterUserIdsAfter(7L, 9L, page)).thenReturn(List.of(9L));
        when(policies.findReporterUserIdsAfter(9L, 9L, page)).thenReturn(List.of());
        when(recalculation.recalculate(2L)).thenReturn(result(true, true));
        when(recalculation.recalculate(7L)).thenReturn(result(false, false));
        when(recalculation.recalculate(9L)).thenReturn(result(true, false));

        var response = service.recalculate();

        assertThat(response.processedCount()).isEqualTo(2);
        assertThat(response.changedCount()).isEqualTo(1);
        verify(policies).findReporterUserIdsAfter(9L, 9L, page);
        verify(policies, never()).findAll();
        verify(policies, never()).count();
    }

    @Test
    void stopsAfterFailedPolicyWithoutProcessingLaterIds() {
        when(policies.findMaxReporterUserId()).thenReturn(9L);
        when(policies.findReporterUserIdsAfter(null, 9L, PageRequest.of(0, 100)))
                .thenReturn(List.of(2L, 7L, 9L));
        when(recalculation.recalculate(2L)).thenReturn(result(true, true));
        RuntimeException failure = new IllegalStateException("history write failed");
        when(recalculation.recalculate(7L)).thenThrow(failure);

        assertThatThrownBy(service::recalculate).isSameAs(failure);

        verify(recalculation, never()).recalculate(9L);
    }

    private TrustScoreRecalculationService.RecalculationResult result(boolean processed, boolean changed) {
        return new TrustScoreRecalculationService.RecalculationResult(processed, changed);
    }
}
