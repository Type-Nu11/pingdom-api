package com.typenull.pingdom.performance.fixture;

import java.util.List;

public record PerformanceLoadScenario(
        String name,
        PerformanceLoadScenarioType type,
        String endpoint,
        int requestCount,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
