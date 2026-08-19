package com.typenull.pingdom.fixture.place;

public record PlaceVisitDecisionScenario(
        String name,
        PlaceVisitDecisionScenarioType type,
        int expectedStatus,
        String expectedErrorCode,
        String assertion
) {
}
