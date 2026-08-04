package com.typenull.pingdom.moderation.application.service.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalySeverity;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionTrigger;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreAnomalyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreInterventionRuleRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreAnomalyResolveRequest;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreInterventionRuleRequest;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTrustScoreServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T09:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private TrustScoreAnomalyRepository trustScoreAnomalyRepository;
    @Mock
    private TrustScoreInterventionRuleRepository trustScoreInterventionRuleRepository;
    @Mock
    private ReporterModerationPolicyRepository reporterModerationPolicyRepository;
    @Mock
    private AdminAuditLogService adminAuditLogService;

    private AdminTrustScoreService service;

    @BeforeEach
    void setUp() {
        service = new AdminTrustScoreService(
                trustScoreAnomalyRepository,
                trustScoreInterventionRuleRepository,
                reporterModerationPolicyRepository,
                adminAuditLogService,
                CLOCK
        );
    }

    @Test
    void resolveAnomaly_updatesResolutionAndRecordsAuditLog() {
        TrustScoreAnomaly anomaly = TrustScoreAnomaly.builder()
                .id(1L)
                .reporterUserId(10L)
                .reporterUsername("reporter")
                .anomalyType(TrustScoreAnomalyType.FALSE_REPORT_SPIKE)
                .severity(TrustScoreAnomalySeverity.HIGH)
                .baselineScore(100)
                .observedScore(40)
                .submittedCount(5)
                .acceptedCount(1)
                .declinedCount(4)
                .falseReportCount(4)
                .detectedAt(LocalDateTime.of(2026, 7, 20, 17, 0))
                .build();
        when(trustScoreAnomalyRepository.findById(1L)).thenReturn(Optional.of(anomaly));

        var response = service.resolveAnomaly(
                1L,
                new AdminTrustScoreAnomalyResolveRequest("관리자 검토 완료"),
                99L
        );

        assertThat(response.resolvedAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 18, 0));
        assertThat(response.resolutionReason()).isEqualTo("관리자 검토 완료");
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRule_rejectsNonTemporaryRestrictDuration() {
        AdminTrustScoreInterventionRuleRequest request = new AdminTrustScoreInterventionRuleRequest(
                "warn rule",
                TrustScoreInterventionTrigger.TRUST_SCORE_RANGE,
                TrustScoreInterventionAction.WARN,
                0,
                60,
                0,
                0,
                7,
                10,
                "warning only"
        );

        assertThatThrownBy(() -> service.createRule(request, 99L))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode")
                .isEqualTo(AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST);
    }

    @Test
    void evaluateReporter_appliesHighestPriorityTemporaryRestriction() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(10L)
                .reporterUsername("reporter")
                .submittedCount(5)
                .acceptedCount(1)
                .declinedCount(4)
                .falseReportCount(4)
                .trustScore(40)
                .build();
        TrustScoreInterventionRule rule = TrustScoreInterventionRule.builder()
                .id(3L)
                .ruleName("temporary restriction")
                .triggerType(TrustScoreInterventionTrigger.FALSE_REPORT_COUNT)
                .actionType(TrustScoreInterventionAction.TEMPORARY_RESTRICT)
                .enabled(true)
                .minTrustScore(0)
                .maxTrustScore(60)
                .minSubmittedCount(3)
                .minFalseReportCount(3)
                .durationDays(7)
                .priority(10)
                .reason("허위 신고 누적")
                .build();
        when(reporterModerationPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(rule));

        var response = service.evaluateReporter(10L, 99L);

        assertThat(response.matchedRuleId()).isEqualTo(3L);
        assertThat(response.restrictedUntil()).isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        assertThat(policy.getRestrictedUntil()).isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        assertThat(policy.getRestrictionReason()).isEqualTo("허위 신고 누적");
        verify(adminAuditLogService).record(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq(AdminAuditAction.TRUST_SCORE_INTERVENTION_EVALUATED),
                org.mockito.ArgumentMatchers.eq(AdminAuditTargetType.TRUST_SCORE_INTERVENTION_RULE),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq("허위 신고 누적"),
                any(),
                any()
        );
    }
}
