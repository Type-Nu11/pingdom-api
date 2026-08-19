package com.typenull.pingdom.fixture.moderation.trust.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TrustLayerFixturesTest {

    @Test
    void definesNormalBoundaryAndRestrictedReporters() {
        TrustReporterFixture fixture = TrustLayerFixtures.realisticReporterFixture();

        assertThat(fixture.policies()).hasSize(3)
                .anySatisfy(policy -> assertThat(policy.getRestrictedUntil()).isNotNull())
                .anySatisfy(policy -> assertThat(policy.getRestrictedUntil()).isNull());
        assertThat(fixture.anomalies()).extracting(anomaly -> anomaly.getAnomalyType())
                .contains(TrustScoreAnomalyType.FALSE_REPORT_SPIKE, TrustScoreAnomalyType.RAPID_DROP);
        assertThat(fixture.interventionRules()).anySatisfy(rule -> assertThat(rule.isEnabled()).isTrue());
    }

    @Test
    void scenarioDefinitionsCoverNormalBoundaryAndFailureCases() {
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

    @Test
    void fixtureIdentifiersAreUniqueForDeterministicAssertions() {
        TrustReporterFixture fixture = TrustLayerFixtures.realisticReporterFixture();

        assertThat(fixture.policies().stream().map(policy -> policy.getReporterUserId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.policies().stream().map(policy -> policy.getReporterUserId()).toList()));
        assertThat(fixture.anomalies().stream().map(anomaly -> anomaly.getId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.anomalies().stream().map(anomaly -> anomaly.getId()).toList()));
        assertThat(fixture.interventionRules().stream().map(rule -> rule.getId()).toList())
                .hasSameSizeAs(new HashSet<>(fixture.interventionRules().stream().map(rule -> rule.getId()).toList()));
    }
}
