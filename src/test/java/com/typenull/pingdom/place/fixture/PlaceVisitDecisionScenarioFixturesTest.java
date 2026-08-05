package com.typenull.pingdom.place.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
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
}
