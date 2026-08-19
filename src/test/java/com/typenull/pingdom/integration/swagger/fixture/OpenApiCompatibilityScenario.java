package com.typenull.pingdom.integration.swagger.fixture;

import java.util.List;

public record OpenApiCompatibilityScenario(
        String name,
        OpenApiCompatibilityDomain domain,
        OpenApiCompatibilityScenarioType type,
        String path,
        String method,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
