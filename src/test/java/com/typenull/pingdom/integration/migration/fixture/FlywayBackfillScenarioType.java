package com.typenull.pingdom.integration.migration.fixture;

/**
 * backfill fixture가 정상 적용, 입력 경계, 실패, 재실행을 각각 다루는지 분류.
 */
public enum FlywayBackfillScenarioType {
    NORMAL,
    BOUNDARY,
    FAILURE,
    RETRY
}
