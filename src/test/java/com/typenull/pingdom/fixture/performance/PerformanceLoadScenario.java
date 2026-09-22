package com.typenull.pingdom.fixture.performance;

import java.util.List;

/** 부하 시나리오의 요청 수와 기대 HTTP 상태·오류·검증 항목을 담음. 측정 결과·실행 보장과 구분되는 기대 계약. */
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
