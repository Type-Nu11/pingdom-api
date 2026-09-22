package com.typenull.pingdom.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * 인증 경계 fixture의 역할 범위·식별자·검증 설명을 검사한다. 실제 API 권한은 다른 통합 테스트가 검증한다.
 */
class AuthorizationBoundaryFixturesTest {
    /**
     * fixture가 주요 역할과 유효한 HTTP 경로·검증 설명을 갖고 실패 시나리오에는 오류 코드를 명시하는지 확인한다.
     */
    @Test
    void boundaryFixtureContract() {
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

    /**
     * 진단 결과가 같은 이름으로 혼동되지 않도록 모든 인증 경계 fixture 이름의 유일성을 확인한다.
     */
    @Test
    void uniqueScenarioNames() {
        var names = AuthorizationBoundaryFixtures.scenarios().stream()
                .map(AuthorizationBoundaryScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
