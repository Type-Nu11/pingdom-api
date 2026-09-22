package com.typenull.pingdom.fixture.moderation.trust.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TrustLayerFixturesTest {

    /**
     * 신고자 정책 3건에 제한 여부 두 상태가 있고, 허위 신고 급증·급락 및 활성 개입 규칙이 포함되는지 검증.
     */
    @Test
    void definesReporterPolicyBoundaries() {
        TrustReporterFixture fixture = TrustLayerFixtures.realisticReporterFixture();

        assertThat(fixture.policies()).hasSize(3)
                .anySatisfy(policy -> assertThat(policy.getRestrictedUntil()).isNotNull())
                .anySatisfy(policy -> assertThat(policy.getRestrictedUntil()).isNull());
        assertThat(fixture.anomalies()).extracting(anomaly -> anomaly.getAnomalyType())
                .contains(TrustScoreAnomalyType.FALSE_REPORT_SPIKE, TrustScoreAnomalyType.RAPID_DROP);
        assertThat(fixture.interventionRules()).anySatisfy(rule -> assertThat(rule.isEnabled()).isTrue());
    }

    /**
     * 정상·경계·실패 분류와 검증 항목을 확인하고 실패 사례가 신고자 없음·규칙 요청 오류 코드를 포함하는지 검증.
     */
    @Test
    void coversTrustScenarioCategories() {
        TrustReporterFixture fixture = TrustLayerFixtures.realisticReporterFixture();

        assertThat(fixture.scenarios()).extracting(TrustScenario::type)
                .contains(TrustScenarioType.NORMAL, TrustScenarioType.BOUNDARY, TrustScenarioType.FAILURE);
        assertThat(fixture.scenarios()).allSatisfy(scenario -> assertThat(scenario.assertions())
                .as("%s assertion labels", scenario.name()).isNotEmpty());
        assertThat(fixture.scenarios()).filteredOn(scenario -> scenario.type() == TrustScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode()).isNotBlank());
        assertThat(fixture.scenarios()).extracting(TrustScenario::expectedErrorCode)
                .contains(AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND.name(),
                        AdminErrorCode.TRUST_SCORE_INTERVENTION_RULE_INVALID_REQUEST.name());
    }

    /**
     * 신고자 ID·이상 징후 ID·개입 규칙 ID가 각 목록에서 유일한지 확인해 assertion 대상의 충돌을 방지.
     */
    @Test
    void usesUniqueTrustIdentifiers() {
        TrustReporterFixture fixture = TrustLayerFixtures.realisticReporterFixture();

        assertThat(fixture.policies().stream().map(policy -> policy.getReporterUserId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.policies().stream().map(policy -> policy.getReporterUserId()).toList()));
        assertThat(fixture.anomalies().stream().map(anomaly -> anomaly.getId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.anomalies().stream().map(anomaly -> anomaly.getId()).toList()));
        assertThat(fixture.interventionRules().stream().map(rule -> rule.getId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.interventionRules().stream().map(rule -> rule.getId()).toList()));
    }
}
