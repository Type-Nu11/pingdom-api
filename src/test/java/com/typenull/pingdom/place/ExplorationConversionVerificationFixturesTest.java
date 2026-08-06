package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void verifiesEachLoopContract(ExplorationConversionVerificationScenario scenario) {
        assertThat(scenario.method()).isIn("GET", "POST");
        assertThat(scenario.path()).startsWith("/");
        assertThat(scenario.expectedStatus()).isBetween(200, 299);
        assertThat(scenario.assertions()).allSatisfy(assertion -> assertThat(assertion).isNotBlank());
    }

    private static Stream<ExplorationConversionVerificationScenario> scenarios() {
        return ExplorationConversionVerificationFixtures.scenarios().stream();
    }
}
