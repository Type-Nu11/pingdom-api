package com.typenull.pingdom.fixture.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 방문 판단 테스트 fixture가 일관된 상태와 입력을 제공하는지 검증합니다. */
class PlaceVisitDecisionScenarioFixturesTest {

    /**
     * 방문 판단 시나리오가 정상·경계·인가·실패 enum 값을 중복 없이 모두 포함하는지 검증한다.
     */
    @Test
    void coversVisitDecisionCategories() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .extracting(PlaceVisitDecisionScenario::type)
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PlaceVisitDecisionScenarioType.class));
    }

    /**
     * 모든 방문 판단 시나리오에 이름과 검증 문구가 있고 HTTP 상태가 200~499 범위인지 검증한다.
     */
    @Test
    void providesScenarioDiagnostics() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .allSatisfy(scenario -> {
                    assertThat(scenario.name()).isNotBlank();
                    assertThat(scenario.assertion()).isNotBlank();
                    assertThat(scenario.expectedStatus()).isBetween(200, 499);
                });
    }

    /**
     * 인가 및 실패 시나리오에 비어 있지 않은 오류 코드가 지정되어 실패 원인을 식별할 수 있는지 검증한다.
     */
    @Test
    void declaresFailureErrorCodes() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios())
                .filteredOn(scenario -> scenario.type() == PlaceVisitDecisionScenarioType.AUTHORIZATION
                        || scenario.type() == PlaceVisitDecisionScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode()).isNotBlank());
    }

    /**
     * 방문 판단 시나리오 이름 집합의 크기가 원본과 같은지 확인해 중복 사례 이름을 방지한다.
     */
    @Test
    void usesUniqueFixtureNames() {
        assertThat(PlaceVisitDecisionScenarioFixtures.scenarios().stream()
                .map(PlaceVisitDecisionScenario::name)
                .collect(Collectors.toSet()))
                .hasSameSizeAs(PlaceVisitDecisionScenarioFixtures.scenarios());
    }
}
