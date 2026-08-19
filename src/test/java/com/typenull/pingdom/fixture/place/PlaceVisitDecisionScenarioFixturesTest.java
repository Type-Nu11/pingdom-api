package com.typenull.pingdom.fixture.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionScenarioFixturesTest {

    @Test
    void coversNormalBoundaryAuthorizationAndFailureScenarios() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .extracting(PlaceVisitDecisionScenario::type)
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PlaceVisitDecisionScenarioType.class));
    }

    @Test
    void providesDiagnosticAssertionsForEveryScenario() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .allSatisfy(scenario -> {
                    assertThat(scenario.name()).isNotBlank();
                    assertThat(scenario.assertion()).isNotBlank();
                    assertThat(scenario.expectedStatus()).isBetween(200, 499);
                });
    }

    @Test
    void declaresErrorCodesForAuthorizationAndFailureScenarios() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .filteredOn(scenario -> scenario.type() == PlaceVisitDecisionScenarioType.AUTHORIZATION
                        || scenario.type() == PlaceVisitDecisionScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode()).isNotBlank());
    }

    @Test
    void usesUniqueFixtureNamesForFailureDiagnosis() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios().stream()
                .map(PlaceVisitDecisionScenario::name)
                .collect(Collectors.toSet()))
                .hasSameSizeAs(PlaceVisitDecisionScenarioFixtures.scenarios());
    }
}
