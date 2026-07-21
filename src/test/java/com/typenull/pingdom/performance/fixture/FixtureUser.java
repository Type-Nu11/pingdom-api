package com.typenull.pingdom.performance.fixture;

import com.typenull.pingdom.identity.domain.UserRole;

public record FixtureUser(
        long id,
        String username,
        UserRole role,
        boolean active
) {
}
