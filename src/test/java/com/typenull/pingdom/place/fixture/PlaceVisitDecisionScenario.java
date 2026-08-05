package com.typenull.pingdom.place.fixture;

public record PlaceVisitDecisionScenario(
        String name,
        PlaceVisitDecisionScenarioType type,
        int expectedStatus,
        String expectedErrorCode,
        String assertion
) {
}
