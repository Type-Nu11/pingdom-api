package com.typenull.pingdom.fixture.moderation.trust.fixture;

import java.util.List;

/** 신뢰도 API 시나리오의 요청 문자열과 기대 상태·오류·진단 항목을 보관. */
public record TrustScenario(
        String name,
        TrustScenarioType type,
        String endpoint,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
