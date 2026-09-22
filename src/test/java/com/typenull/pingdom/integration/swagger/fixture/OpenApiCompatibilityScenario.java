package com.typenull.pingdom.integration.swagger.fixture;

import java.util.List;

/**
 * 문서 그룹의 경로·메서드·상태와 진단 설명을 묶음. 오류 코드 대신 오류 설명을 쓰는 시나리오는 expectedErrorCode가 null.
 */
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
