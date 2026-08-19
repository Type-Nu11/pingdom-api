package com.typenull.pingdom.integration.migration.fixture;

import java.util.List;

public record FlywayBackfillScenario(
        String name,
        String migrationVersion,
        String targetTable,
        FlywayBackfillScenarioType type,
        String legacyData,
        String expectedBackfill,
        String expectedFailureReason,
        List<String> assertions
) {
}
