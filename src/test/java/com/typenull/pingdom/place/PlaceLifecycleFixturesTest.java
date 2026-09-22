package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PlaceLifecycleFixturesTest {
    /**
     * fixture에 노출·운영 상태·장소 경로가 포함되고 오류 시나리오에 코드가 있는지 확인. 실제 상태 변경 API 호출은 검증 범위에서 제외.
     */
    @Test
    void coversLifecycleFixtureContracts() {
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

    /**
     * 시나리오 이름의 중복을 막아 실패한 fixture를 이름으로 식별할 수 있게 함.
     */
    @Test
    void keepsScenarioNamesUnique() {
        var names = PlaceLifecycleFixtures.scenarios().stream().map(PlaceLifecycleScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
