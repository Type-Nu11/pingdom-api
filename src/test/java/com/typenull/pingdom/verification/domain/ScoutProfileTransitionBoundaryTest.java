package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutProfileTransitionBoundaryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);

    @Test
    void pendingProfileCannotBeRevokedBeforeApproval() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);

        assertThatThrownBy(() -> profile.revoke(99L, "회수", CREATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }
}
