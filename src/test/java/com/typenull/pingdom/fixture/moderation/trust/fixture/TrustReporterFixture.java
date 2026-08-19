package com.typenull.pingdom.fixture.moderation.trust.fixture;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import java.util.List;

public record TrustReporterFixture(
        List<ReporterModerationPolicy> policies,
        List<TrustScoreAnomaly> anomalies,
        List<TrustScoreInterventionRule> interventionRules,
        List<TrustScenario> scenarios
) {
}
