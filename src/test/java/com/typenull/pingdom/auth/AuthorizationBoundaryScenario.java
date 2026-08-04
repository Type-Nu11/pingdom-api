package com.typenull.pingdom.auth;

import java.util.List;

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
