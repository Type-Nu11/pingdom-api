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

    /**
     * 사용자·활성 역할 저장소와 고정 Clock을 연결해 관리자 세부 권한 판정을 검증한다.
     */
    @BeforeEach
    void setUp() {
        authorizationService = new AdminRoleAuthorizationService(userRepository, assignmentRepository, CLOCK);
    }

    /**
     * ADMIN 사용자에게 활성 SUPER_ADMIN이 있으면 역할 관리 권한 검사를 예외 없이 통과하는지 검증한다.
     */
    @Test
    void activeSuperAdminCanManageRoles() {
        User admin = user(10L, UserRole.ADMIN);
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findAllByAdminUserIdAndStatus(10L, AdminRoleAssignmentStatus.ACTIVE))
                .thenReturn(List.of(AdminRoleAssignment.assign(10L, AdminRole.SUPER_ADMIN, 10L, now())));

        authorizationService.requirePermission(10L, AdminPermission.ADMIN_ROLE_MANAGE);
    }

    /**
     * ADMIN 사용자라도 ANALYST 역할만 있으면 ADMIN_PERMISSION_REQUIRED로 역할 관리를 거절하는지 검증한다.
     */
    @Test
    void rejectsAnalystRoleManagement() {
        User admin = user(10L, UserRole.ADMIN);
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findAllByAdminUserIdAndStatus(10L, AdminRoleAssignmentStatus.ACTIVE))
                .thenReturn(List.of(AdminRoleAssignment.assign(10L, AdminRole.ANALYST, 20L, now())));

        assertThatThrownBy(() -> authorizationService.requirePermission(10L, AdminPermission.ADMIN_ROLE_MANAGE))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.ADMIN_PERMISSION_REQUIRED));
    }

    /**
     * 지정 ID·역할의 사용자를 만들어 기본 사용자 역할과 세부 관리자 권한을 분리 검증한다.
     */
    private User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .username("admin-" + id)
                .email("admin-" + id + "@example.com")
                .role(role)
                .build();
    }

    /**
     * 고정 Clock의 시각을 관리자 역할 부여 입력으로 제공한다.
     */
    private java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
    }
}
