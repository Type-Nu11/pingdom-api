package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutProfileTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 8, 2, 9, 0);

    @Test
    void pendingProfileCanBeActivatedByAnAdmin() {
        ScoutProfile profile = ScoutProfile.pending(10L, " 현장 Scout ", " 장소 정보를 확인합니다. ", CREATED_AT);

        profile.activate(99L, REVIEWED_AT);

        assertThat(profile.getUserId()).isEqualTo(10L);
        assertThat(profile.getDisplayName()).isEqualTo("현장 Scout");
        assertThat(profile.getIntroduction()).isEqualTo("장소 정보를 확인합니다.");
        assertThat(profile.getStatus()).isEqualTo(ScoutProfileStatus.ACTIVE);
        assertThat(profile.getReviewedByAdminUserId()).isEqualTo(99L);
        assertThat(profile.getReviewedAt()).isEqualTo(REVIEWED_AT);
    }

    @Test
    void suspendedAndRevokedProfilesRequireAReason() {
        ScoutProfile profile = ScoutProfile.pending(10L, "Scout", null, CREATED_AT);
        profile.activate(99L, REVIEWED_AT);

        assertThatThrownBy(() -> profile.suspend(99L, "  ", REVIEWED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        profile.suspend(99L, "검증 자료 부족", REVIEWED_AT);

        assertThat(profile.getStatus()).isEqualTo(ScoutProfileStatus.SUSPENDED);
        assertThat(profile.getStatusReason()).isEqualTo("검증 자료 부족");
    }
}
