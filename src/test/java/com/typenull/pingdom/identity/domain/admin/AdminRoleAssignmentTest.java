package com.typenull.pingdom.identity.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminRoleAssignmentTest {

    private static final LocalDateTime ASSIGNED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    /**
     * SUPER_ADMIN 부여 시 사용자·감사 조회 권한을 허용하고 해제하면 비활성·해제 시각과 사용자 조회 거절을 반영하는지 검증한다.
     */
    @Test
    void revokesSuperAdminPermissions() {
        AdminRoleAssignment assignment = AdminRoleAssignment.assign(
                10L,
                AdminRole.SUPER_ADMIN,
                20L,
                ASSIGNED_AT
        );

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.allows(AdminPermission.USER_READ)).isTrue();
        assertThat(assignment.allows(AdminPermission.AUDIT_READ)).isTrue();

        assignment.revoke(ASSIGNED_AT.plusHours(1));

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getRevokedAt()).isEqualTo(ASSIGNED_AT.plusHours(1));
        assertThat(assignment.allows(AdminPermission.USER_READ)).isFalse();
    }

    /**
     * CONTENT_MODERATOR는 장소 검토를 허용하지만 사용자 제재 권한은 주지 않는지 검증한다.
     */
    @Test
    void limitsSpecializedAdminPermissions() {
        AdminRoleAssignment assignment = AdminRoleAssignment.assign(
                10L,
                AdminRole.CONTENT_MODERATOR,
                20L,
                ASSIGNED_AT
        );

        assertThat(assignment.allows(AdminPermission.PLACE_MODERATE)).isTrue();
        assertThat(assignment.allows(AdminPermission.USER_SANCTION)).isFalse();
    }

    /**
     * 역할 부여 시각보다 이른 해제는 IllegalStateException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsRevocationBeforeAssignment() {
        AdminRoleAssignment assignment = AdminRoleAssignment.assign(
                10L,
                AdminRole.ANALYST,
                20L,
                ASSIGNED_AT
        );

        assertThatThrownBy(() -> assignment.revoke(ASSIGNED_AT.minusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
