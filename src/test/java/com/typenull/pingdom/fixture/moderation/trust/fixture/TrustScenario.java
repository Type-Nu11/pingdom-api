package com.typenull.pingdom.fixture.moderation.trust.fixture;

import java.util.List;

public record TrustScenario(
        String name,
        TrustScenarioType type,
        String endpoint,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
