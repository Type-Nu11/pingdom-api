package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PlaceLifecycleFixturesTest {
    @Test
    void coversVisibilityDeletionAndClosureLifecycleContracts() {
        var scenarios = PlaceLifecycleFixtures.scenarios();

        assertThat(scenarios).extracting(PlaceLifecycleScenario::endpoint)
                .anyMatch(endpoint -> endpoint.contains("discovery-status"))
                .anyMatch(endpoint -> endpoint.contains("operating-status"))
                .anyMatch(endpoint -> endpoint.startsWith("/places/"));
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.method()).isIn("GET", "PATCH", "DELETE");
            assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion -> assertThat(assertion).isNotBlank());
            if (scenario.expectedStatus() >= 400) {
                assertThat(scenario.expectedErrorCode()).as(scenario.name()).isNotBlank();
            }
        });
    }

    @Test
    void scenarioNamesAreUniqueForFailureDiagnosis() {
        var names = PlaceLifecycleFixtures.scenarios().stream().map(PlaceLifecycleScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
