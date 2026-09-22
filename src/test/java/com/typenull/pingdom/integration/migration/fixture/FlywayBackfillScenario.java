package com.typenull.pingdom.integration.migration.fixture;

import java.util.List;

/**
 * migration 버전·대상 테이블·기존 데이터·기대값을 묶은 backfill 설명이다. 실패 유형 외에는 실패 사유가 null일 수 있다.
 */
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
