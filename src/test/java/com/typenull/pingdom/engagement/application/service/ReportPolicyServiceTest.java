package com.typenull.pingdom.engagement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionTrigger;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreAnomalyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreInterventionRuleRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportPolicyServiceTest {

    private static final Long REPORTER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 12, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private ReporterModerationPolicyRepository reporterPolicyRepository;
    @Mock
    private PostReportRepository postReportRepository;
    @Mock
    private TrustScoreAnomalyRepository trustScoreAnomalyRepository;
    @Mock
    private TrustScoreInterventionRuleRepository trustScoreInterventionRuleRepository;
    @Mock
    private PlaceGrowthService placeGrowthService;

    private ReportPolicyService service;

    @BeforeEach
    void setUp() {
        service = new ReportPolicyService(
                reporterPolicyRepository,
                postReportRepository,
                trustScoreAnomalyRepository,
                trustScoreInterventionRuleRepository,
                placeGrowthService,
                CLOCK
        );
        when(trustScoreAnomalyRepository.findByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(REPORTER_ID))
                .thenReturn(List.of());
        when(trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());
    }

    @Test
    void recordDeclinedRestrictsReporterAndRecordsDistinctAnomaliesAtThreshold() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(REPORTER_ID)
                .reporterUsername("reporter")
                .submittedCount(5L)
                .declinedCount(2L)
                .falseReportCount(2L)
                .trustScore(60)
                .build();
        when(reporterPolicyRepository.findByReporterUserIdForUpdate(REPORTER_ID)).thenReturn(Optional.of(policy));

        service.recordDeclined(REPORTER_ID, "reporter", NOW);

        assertThat(policy.getTrustScore()).isEqualTo(40);
        assertThat(policy.getRestrictedUntil()).isEqualTo(NOW.plusDays(7));
        assertThat(policy.getRestrictionReason()).isEqualTo("FALSE_REPORT_THRESHOLD_EXCEEDED");
        ArgumentCaptor<TrustScoreAnomaly> anomalyCaptor = ArgumentCaptor.forClass(TrustScoreAnomaly.class);
        verify(trustScoreAnomalyRepository, org.mockito.Mockito.times(2)).save(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getAllValues())
                .extracting(TrustScoreAnomaly::getAnomalyType)
                .containsExactlyInAnyOrder(TrustScoreAnomalyType.FALSE_REPORT_SPIKE, TrustScoreAnomalyType.LOW_ACCEPTANCE_RATE);
        verify(reporterPolicyRepository).save(policy);
    }

    @Test
    void recordDeclinedDoesNotCreateDuplicateUnresolvedAnomaly() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(REPORTER_ID)
                .reporterUsername("reporter")
                .submittedCount(5L)
                .declinedCount(2L)
                .falseReportCount(2L)
                .trustScore(60)
                .build();
        TrustScoreAnomaly existingSpike = TrustScoreAnomaly.builder()
                .reporterUserId(REPORTER_ID)
                .anomalyType(TrustScoreAnomalyType.FALSE_REPORT_SPIKE)
                .build();
        when(reporterPolicyRepository.findByReporterUserIdForUpdate(REPORTER_ID)).thenReturn(Optional.of(policy));
        when(trustScoreAnomalyRepository.findByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(REPORTER_ID))
                .thenReturn(List.of(existingSpike));

        service.recordDeclined(REPORTER_ID, "reporter", NOW);

        ArgumentCaptor<TrustScoreAnomaly> anomalyCaptor = ArgumentCaptor.forClass(TrustScoreAnomaly.class);
        verify(trustScoreAnomalyRepository).save(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getValue().getAnomalyType()).isEqualTo(TrustScoreAnomalyType.LOW_ACCEPTANCE_RATE);
    }

    @Test
    void recordAcceptedAppliesOnlyHighestPriorityTemporaryRestrictionRule() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(REPORTER_ID)
                .reporterUsername("reporter")
                .submittedCount(5L)
                .declinedCount(3L)
                .falseReportCount(3L)
                .trustScore(40)
                .build();
        when(reporterPolicyRepository.findByReporterUserIdForUpdate(REPORTER_ID)).thenReturn(Optional.of(policy));
        when(trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(restrictionRule(1L, 10, 3), restrictionRule(2L, 20, 14)));

        service.recordAccepted(REPORTER_ID, "reporter");

        assertThat(policy.getTrustScore()).isEqualTo(45);
        assertThat(policy.getRestrictedUntil()).isEqualTo(NOW.plusDays(3));
        assertThat(policy.getRestrictionReason()).isEqualTo("priority-10");
        verify(reporterPolicyRepository).save(policy);
    }

    private TrustScoreInterventionRule restrictionRule(Long id, int priority, int durationDays) {
        return TrustScoreInterventionRule.builder()
                .id(id)
                .ruleName("rule-" + priority)
                .triggerType(TrustScoreInterventionTrigger.FALSE_REPORT_COUNT)
                .actionType(TrustScoreInterventionAction.TEMPORARY_RESTRICT)
                .enabled(true)
                .minTrustScore(0)
                .maxTrustScore(100)
                .minSubmittedCount(0)
                .minFalseReportCount(3)
                .durationDays(durationDays)
                .priority(priority)
                .reason("priority-" + priority)
                .build();
    }
}
