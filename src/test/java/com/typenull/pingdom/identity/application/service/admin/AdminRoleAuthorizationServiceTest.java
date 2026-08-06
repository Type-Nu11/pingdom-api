package com.typenull.pingdom.identity.application.service.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRoleAuthorizationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private AdminRoleAssignmentRepository assignmentRepository;

    private AdminRoleAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AdminRoleAuthorizationService(userRepository, assignmentRepository, CLOCK);
    }

    @Test
    void activeSuperAdminCanManageRoles() {
        User admin = user(10L, UserRole.ADMIN);
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findAllByAdminUserIdAndStatus(10L, AdminRoleAssignmentStatus.ACTIVE))
                .thenReturn(List.of(AdminRoleAssignment.assign(10L, AdminRole.SUPER_ADMIN, 10L, now())));

        authorizationService.requirePermission(10L, AdminPermission.ADMIN_ROLE_MANAGE);
    }

    @Test
    void specializedAdminCannotManageRolesWithoutPermission() {
        User admin = user(10L, UserRole.ADMIN);
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findAllByAdminUserIdAndStatus(10L, AdminRoleAssignmentStatus.ACTIVE))
                .thenReturn(List.of(AdminRoleAssignment.assign(10L, AdminRole.ANALYST, 20L, now())));

        assertThatThrownBy(() -> authorizationService.requirePermission(10L, AdminPermission.ADMIN_ROLE_MANAGE))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.ADMIN_PERMISSION_REQUIRED));
    }

    private User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .username("admin-" + id)
                .email("admin-" + id + "@example.com")
                .role(role)
                .build();
    }

    private java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
    }
}
