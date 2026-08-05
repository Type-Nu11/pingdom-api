package com.typenull.pingdom.moderation.trust.fixture;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalySeverity;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionTrigger;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import java.time.LocalDateTime;
import java.util.List;

public final class TrustLayerFixtures {

    private static final LocalDateTime DETECTED_AT = LocalDateTime.of(2026, 7, 20, 18, 0);

    private TrustLayerFixtures() {
    }

    public static TrustReporterFixture realisticReporterFixture() {
        return new TrustReporterFixture(policies(), anomalies(), interventionRules(), scenarios());
    }

    private static List<ReporterModerationPolicy> policies() {
        return List.of(
                ReporterModerationPolicy.builder().reporterUserId(1001L).reporterUsername("normal-reporter")
                        .submittedCount(10).acceptedCount(8).declinedCount(2).falseReportCount(2).trustScore(100).build(),
                ReporterModerationPolicy.builder().reporterUserId(1002L).reporterUsername("boundary-reporter")
                        .submittedCount(3).acceptedCount(0).declinedCount(3).falseReportCount(3).trustScore(40).build(),
                ReporterModerationPolicy.builder().reporterUserId(1003L).reporterUsername("restricted-reporter")
                        .submittedCount(5).acceptedCount(1).declinedCount(4).falseReportCount(4).trustScore(40)
                        .restrictedUntil(LocalDateTime.of(2026, 7, 27, 18, 0)).restrictionReason("허위 신고 누적").build()
        );
    }

    private static List<TrustScoreAnomaly> anomalies() {
        return List.of(
                TrustScoreAnomaly.builder().id(2001L).reporterUserId(1002L).reporterUsername("boundary-reporter")
                        .anomalyType(TrustScoreAnomalyType.FALSE_REPORT_SPIKE).severity(TrustScoreAnomalySeverity.HIGH)
                        .baselineScore(100).observedScore(40).submittedCount(3).acceptedCount(0).declinedCount(3)
                        .falseReportCount(3).detectedAt(DETECTED_AT).build(),
                TrustScoreAnomaly.builder().id(2002L).reporterUserId(1003L).reporterUsername("restricted-reporter")
                        .anomalyType(TrustScoreAnomalyType.RAPID_DROP).severity(TrustScoreAnomalySeverity.CRITICAL)
                        .baselineScore(100).observedScore(40).submittedCount(5).acceptedCount(1).declinedCount(4)
                        .falseReportCount(4).detectedAt(DETECTED_AT).build()
        );
    }

    private static List<TrustScoreInterventionRule> interventionRules() {
        return List.of(
                TrustScoreInterventionRule.builder().id(3001L).ruleName("false report restriction")
                        .triggerType(TrustScoreInterventionTrigger.FALSE_REPORT_COUNT)
                        .actionType(TrustScoreInterventionAction.TEMPORARY_RESTRICT).enabled(true)
                        .minTrustScore(0).maxTrustScore(60).minSubmittedCount(3).minFalseReportCount(3)
                        .durationDays(7).priority(10).reason("허위 신고 누적").build(),
                TrustScoreInterventionRule.builder().id(3002L).ruleName("low trust warning")
                        .triggerType(TrustScoreInterventionTrigger.TRUST_SCORE_RANGE)
                        .actionType(TrustScoreInterventionAction.WARN).enabled(false)
                        .minTrustScore(0).maxTrustScore(40).priority(20).reason("Trust Score 경계값").build()
        );
    }

    private static List<TrustScenario> scenarios() {
        return List.of(
                new TrustScenario("trust-score-normal-report", TrustScenarioType.NORMAL,
                        "GET /admin/trust-score/reporters/1001", 200, null,
                        List.of("trustGrade", "evidence", "restricted=false")),
                new TrustScenario("trust-score-boundary-intervention", TrustScenarioType.BOUNDARY,
                        "POST /admin/trust-score/reporters/1002/evaluate", 200, null,
                        List.of("minTrustScore/maxTrustScore inclusive", "matchedRuleId", "restrictedUntil")),
                new TrustScenario("trust-score-missing-reporter", TrustScenarioType.FAILURE,
                        "GET /admin/trust-score/reporters/9999", 404,
                        AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND.name(),
                        List.of("HTTP status", "error code", "error message")),
                new TrustScenario("trust-score-invalid-rule-duration", TrustScenarioType.FAILURE,
                        "POST /admin/trust-score/intervention-rules", 400,
                        AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST.name(),
                        List.of("WARN duration rejected", "rule not persisted", "error code"))
        );
    }
}
