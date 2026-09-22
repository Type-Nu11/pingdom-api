package com.typenull.pingdom.fixture.place;

/** 방문 판단 API 시나리오의 분류와 기대 상태·오류·검증 문구를 보관. */
public record PlaceVisitDecisionScenario(
        String name,
        PlaceVisitDecisionScenarioType type,
        int expectedStatus,
        String expectedErrorCode,
        String assertion
) {
}
