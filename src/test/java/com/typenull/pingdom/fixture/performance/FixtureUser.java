package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.identity.domain.UserRole;

/** 부하 시나리오에서 권한과 탈퇴 경계를 재현할 사용자의 ID·역할·활성 상태를 담는다. */
public record FixtureUser(
        long id,
        String username,
        UserRole role,
        boolean active
) {
}
