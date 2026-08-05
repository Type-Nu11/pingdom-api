package com.typenull.pingdom.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class AuthorizationBoundaryFixturesTest {
    @Test
    void locksAuthenticationAndRoleBoundaryScenarios() {
        var scenarios = AuthorizationBoundaryFixtures.scenarios();

        assertThat(scenarios).extracting(AuthorizationBoundaryScenario::actorRole)
                .contains("ANONYMOUS", "USER", "MERCHANT_OWNER", "ADMIN");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.method()).isIn("GET", "POST");
            assertThat(scenario.endpoint()).startsWith("/");
            assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion -> assertThat(assertion).isNotBlank());
            if (scenario.expectedStatus() >= 400) {
                assertThat(scenario.expectedErrorCode()).as(scenario.name()).isNotBlank();
            }
        });
    }

    @Test
    void boundaryScenarioNamesAreUniqueForDiagnosticReports() {
        var names = AuthorizationBoundaryFixtures.scenarios().stream()
                .map(AuthorizationBoundaryScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
