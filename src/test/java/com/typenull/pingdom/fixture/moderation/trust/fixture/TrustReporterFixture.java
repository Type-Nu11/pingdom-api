package com.typenull.pingdom.fixture.moderation.trust.fixture;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import java.util.List;

/** 신고자 정책·이상 징후·개입 규칙·시나리오를 관련된 입력 집합으로 전달. */
public record TrustReporterFixture(
        List<ReporterModerationPolicy> policies,
        List<TrustScoreAnomaly> anomalies,
        List<TrustScoreInterventionRule> interventionRules,
        List<TrustScenario> scenarios
) {
}
