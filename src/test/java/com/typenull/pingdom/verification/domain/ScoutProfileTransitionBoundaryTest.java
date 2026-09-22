package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutProfileTransitionBoundaryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);

    /** 승인 전 PENDING 프로필의 회수 요청은 상태 전이 오류로 거부한다. */
    @Test
    void rejectPendingRevocation() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);

        assertThatThrownBy(() -> profile.revoke(99L, "회수", CREATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 승인 전 PENDING 프로필은 정지할 수 없어 IllegalStateException이 발생해야 한다. */
    @Test
    void rejectPendingSuspension() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);

        assertThatThrownBy(() -> profile.suspend(99L, "심사 보류", CREATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 승인 후 정지한 프로필을 다시 승인하면 ACTIVE 상태로 돌아오는지 확인한다. */
    @Test
    void reactivateSuspendedProfile() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);
        profile.activate(99L, CREATED_AT.plusDays(1));
        profile.suspend(99L, "추가 확인", CREATED_AT.plusDays(2));

        profile.activate(99L, CREATED_AT.plusDays(3));

        org.assertj.core.api.Assertions.assertThat(profile.getStatus()).isEqualTo(ScoutProfileStatus.ACTIVE);
    }

    /** 승인 후 정지된 프로필을 회수하면 REVOKED 상태와 새 회수 사유를 보관해야 한다. */
    @Test
    void revokeSuspendedProfile() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);
        profile.activate(99L, CREATED_AT.plusDays(1));
        profile.suspend(99L, "추가 확인", CREATED_AT.plusDays(2));

        profile.revoke(99L, "자격 회수", CREATED_AT.plusDays(3));

        org.assertj.core.api.Assertions.assertThat(profile.getStatus()).isEqualTo(ScoutProfileStatus.REVOKED);
        org.assertj.core.api.Assertions.assertThat(profile.getStatusReason()).isEqualTo("자격 회수");
    }
}
