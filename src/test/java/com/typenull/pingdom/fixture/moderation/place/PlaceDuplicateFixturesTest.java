package com.typenull.pingdom.fixture.moderation.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PlaceDuplicateFixturesTest {
    /**
     * 중복 후보 fixture에 GET/POST와 확정·거절·병합 경로가 존재하고 모든 검증 항목과 실패 오류 코드가 채워졌는지 확인.
     */
    @Test
    void coversDuplicateResolutionContracts() {
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

    /**
     * 중복 장소 시나리오 이름이 유일한지 확인해 실패 결과를 개별 사례로 식별할 수 있게 함.
     */
    @Test
    void usesUniqueScenarioNames() {
        var names = PlaceDuplicateFixtures.scenarios().stream().map(PlaceDuplicateScenario::name).toList();
        assertThat(names).hasSameSizeAs(new HashSet<>(names));
    }
}
