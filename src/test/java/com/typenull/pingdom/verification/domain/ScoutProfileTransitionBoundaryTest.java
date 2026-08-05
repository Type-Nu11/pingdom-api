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

    @Test
    void pendingProfileCannotBeSuspendedBeforeApproval() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);

        assertThatThrownBy(() -> profile.suspend(99L, "심사 보류", CREATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suspendedProfileCanReturnToActiveAfterReapproval() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);
        profile.activate(99L, CREATED_AT.plusDays(1));
        profile.suspend(99L, "추가 확인", CREATED_AT.plusDays(2));

        profile.activate(99L, CREATED_AT.plusDays(3));

        org.assertj.core.api.Assertions.assertThat(profile.getStatus()).isEqualTo(ScoutProfileStatus.ACTIVE);
    }
}
