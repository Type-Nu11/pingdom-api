package com.typenull.pingdom.swagger.fixture;

import java.util.List;

public record OpenApiCompatibilityScenario(
        String name,
        OpenApiCompatibilityDomain domain,
        OpenApiCompatibilityScenarioType type,
        String path,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
