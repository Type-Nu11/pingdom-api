package com.typenull.pingdom.fixture.performance;

import java.util.List;

/** 부하 시나리오의 요청 수와 기대 HTTP 상태·오류·검증 항목을 담는다. 측정 결과나 실행 보장은 아니다. */
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
