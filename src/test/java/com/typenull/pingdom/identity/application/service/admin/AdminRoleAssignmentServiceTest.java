package com.typenull.pingdom.identity.application.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.event.AdminRoleAssignmentChangedEvent;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentRequest;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AdminRoleAssignmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private AdminRoleAssignmentRepository assignmentRepository;
    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminRoleAssignmentService service;

    /**
     * 사용자·역할·권한·감사·이벤트 의존성 대역과 고정 Clock으로 역할 부여 서비스를 구성한다.
     */
    @BeforeEach
    void setUp() {
        service = new AdminRoleAssignmentService(
                userRepository, assignmentRepository, authorizationService, auditLogService, eventPublisher, CLOCK
        );
    }

    /**
     * 새 관리자 역할 부여가 ACTIVE 응답·역할 관리 권한 확인·부여 사유 감사·해당 역할 변경 이벤트를 생성하는지 검증한다.
     */
    @Test
    void assignsRoleWithAuditAndEvent() {
        User target = admin(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(assignmentRepository.findByAdminUserIdAndRoleAndStatus(
                20L, AdminRole.CONTENT_MODERATOR, AdminRoleAssignmentStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(AdminRoleAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.assign(
                10L,
                20L,
                new AdminRoleAssignmentRequest(AdminRole.CONTENT_MODERATOR, "장소 검수 담당 배정")
        );

        assertThat(response.adminUserId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(AdminRoleAssignmentStatus.ACTIVE);
        verify(authorizationService).requirePermission(
                10L, com.typenull.pingdom.identity.domain.admin.AdminPermission.ADMIN_ROLE_MANAGE
        );
        verify(auditLogService).record(
                eq(10L), eq(AdminAuditAction.ADMIN_ROLE_ASSIGNED),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType.USER),
                eq(20L), eq("장소 검수 담당 배정"), isNull(), any()
        );
        ArgumentCaptor<AdminRoleAssignmentChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(AdminRoleAssignmentChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().role()).isEqualTo(AdminRole.CONTENT_MODERATOR);
    }

    /**
     * 동일한 활성 ANALYST 역할이 이미 있으면 ADMIN_ROLE_ASSIGNMENT_CONFLICT로 거절되는지 검증한다.
     */
    @Test
    void rejectsDuplicateActiveRole() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin(20L)));
        when(assignmentRepository.findByAdminUserIdAndRoleAndStatus(
                20L, AdminRole.ANALYST, AdminRoleAssignmentStatus.ACTIVE
        )).thenReturn(Optional.of(AdminRoleAssignment.assign(20L, AdminRole.ANALYST, 10L, now())));

        assertThatThrownBy(() -> service.assign(
                10L, 20L, new AdminRoleAssignmentRequest(AdminRole.ANALYST, null)
        )).isInstanceOfSatisfying(AdminException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_CONFLICT));
    }

    /**
     * 활성 역할 해제가 REVOKED·해제 시각을 기록하고 기존/변경 상태와 사유를 감사에 전달하는지 검증한다.
     */
    @Test
    void revokesRoleWithHistoricalAudit() {
        AdminRoleAssignment assignment = AdminRoleAssignment.assign(20L, AdminRole.ANALYST, 10L, now());
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin(20L)));
        when(assignmentRepository.findByAdminUserIdAndRoleAndStatus(
                20L, AdminRole.ANALYST, AdminRoleAssignmentStatus.ACTIVE
        )).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        var response = service.revoke(10L, 20L, AdminRole.ANALYST, "업무 변경");

        assertThat(response.status()).isEqualTo(AdminRoleAssignmentStatus.REVOKED);
        assertThat(assignment.getRevokedAt()).isEqualTo(now());
        verify(auditLogService).record(
                eq(10L), eq(AdminAuditAction.ADMIN_ROLE_REVOKED),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType.USER),
                eq(20L), eq("업무 변경"), any(), any()
        );
    }

    /**
     * 지정 ID와 ADMIN 역할을 가진 사용자를 만들어 역할 부여/해제 대상 자격을 충족시킨다.
     */
    private User admin(Long id) {
        return User.builder()
                .id(id)
                .username("admin-" + id)
                .email("admin-" + id + "@example.com")
                .role(UserRole.ADMIN)
                .build();
    }

    /**
     * 역할 부여·해제 assertion에 사용할 고정 Clock의 LocalDateTime을 반환한다.
     */
    private java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
    }
}
