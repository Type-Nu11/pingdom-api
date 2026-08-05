package com.typenull.pingdom.identity.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminRoleAssignmentTest {

    private static final LocalDateTime ASSIGNED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Test
    void superAdminAllowsEveryPermissionAndRoleCanBeRevoked() {
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

    @Test
    void specializedRoleDoesNotReceiveUnrelatedPermission() {
        AdminRoleAssignment assignment = AdminRoleAssignment.assign(
                10L,
                AdminRole.CONTENT_MODERATOR,
                20L,
                ASSIGNED_AT
        );

        assertThat(assignment.allows(AdminPermission.PLACE_MODERATE)).isTrue();
        assertThat(assignment.allows(AdminPermission.USER_SANCTION)).isFalse();
    }

    @Test
    void cannotRevokeAssignmentBeforeItsAssignedAt() {
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
