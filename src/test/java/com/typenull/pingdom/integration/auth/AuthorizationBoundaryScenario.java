package com.typenull.pingdom.integration.auth;

import java.util.List;

/**
 * HTTP 메서드·경로·요청자 역할과 기대 상태를 묶은 인증 경계 fixture. 정상 응답의 expectedErrorCode는 null 허용.
 */
public record AuthorizationBoundaryScenario(
        String name,
        String method,
        String endpoint,
        String actorRole,
        int expectedStatus,
        String expectedErrorCode,
        List<String> assertions
) {
}
