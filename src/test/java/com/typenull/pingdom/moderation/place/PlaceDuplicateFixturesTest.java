package com.typenull.pingdom.moderation.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PlaceDuplicateFixturesTest {
    @Test
    void coversDuplicateDecisionAndMergeContracts() {
        var scenarios = PlaceDuplicateFixtures.scenarios();
        assertThat(scenarios).extracting(PlaceDuplicateScenario::method)
                .contains("GET", "POST");
        assertThat(scenarios).anySatisfy(scenario -> assertThat(scenario.endpoint()).contains("confirm"));
        assertThat(scenarios).anySatisfy(scenario -> assertThat(scenario.endpoint()).contains("reject"));
        assertThat(scenarios).anySatisfy(scenario -> assertThat(scenario.endpoint()).contains("merge"));
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion -> assertThat(assertion).isNotBlank());
            if (scenario.expectedStatus() >= 400) {
                assertThat(scenario.expectedErrorCode()).as(scenario.name()).isNotBlank();
            }
        });
    }

    @Test
    void scenarioNamesAreUniqueForFailureDiagnosis() {
        var names = PlaceDuplicateFixtures.scenarios().stream().map(PlaceDuplicateScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
