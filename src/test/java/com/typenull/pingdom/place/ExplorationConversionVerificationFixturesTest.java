package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExplorationConversionVerificationFixturesTest {
    @Test
    void definesEndToEndExplorationConversionVerificationLoop() {
        var scenarios = ExplorationConversionVerificationFixtures.scenarios();

        assertThat(scenarios).hasSize(3);
        assertThat(scenarios).extracting(ExplorationConversionVerificationScenario::name)
                .containsExactly("explore", "convert", "verify");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.path()).isNotBlank();
            assertThat(scenario.expectedStatus()).isBetween(200, 299);
            assertThat(scenario.assertions()).isNotEmpty();
        });
    }
}
