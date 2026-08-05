package com.typenull.pingdom.moderation.trust;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.trust.fixture.TrustLayerFixtures;
import com.typenull.pingdom.moderation.trust.fixture.TrustReporterFixture;
import com.typenull.pingdom.moderation.trust.fixture.TrustScenario;
import com.typenull.pingdom.moderation.trust.fixture.TrustScenarioType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrustLayerScenarioAutomationTest {

    private static final TrustReporterFixture FIXTURE = TrustLayerFixtures.realisticReporterFixture();

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void everyScenarioDefinesExecutableHttpContract(String name, TrustScenario scenario) {
        assertThat(scenario.name()).isEqualTo(name);
        assertThat(scenario.endpoint()).matches("(GET|POST|PATCH|PUT|DELETE) .+");
        assertThat(scenario.expectedStatus()).isBetween(200, 499);
        assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion ->
                assertThat(assertion).as("diagnostic assertion").isNotBlank());

        if (scenario.type() == TrustScenarioType.FAILURE) {
            assertThat(scenario.expectedStatus()).isGreaterThanOrEqualTo(400);
            assertThat(scenario.expectedErrorCode()).isNotBlank();
        } else {
            assertThat(scenario.expectedStatus()).isLessThan(400);
            assertThat(scenario.expectedErrorCode()).isNull();
        }
    }

    @Test
    void automationFixtureHasAllExecutionCategories() {
        assertThat(FIXTURE.scenarios()).extracting(TrustScenario::type)
                .containsExactlyInAnyOrder(
                        TrustScenarioType.NORMAL,
                        TrustScenarioType.BOUNDARY,
                        TrustScenarioType.FAILURE,
                        TrustScenarioType.FAILURE
                );
    }

    private static Stream<Arguments> scenarios() {
        return FIXTURE.scenarios().stream()
                .map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
