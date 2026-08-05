package com.typenull.pingdom.identity.application.service.admin;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.event.AdminRoleAssignmentChangedEvent;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentRequest;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoleAssignmentService {

    private final UserRepository userRepository;
    private final AdminRoleAssignmentRepository assignmentRepository;
    private final AdminRoleAuthorizationService authorizationService;
    private final AdminAuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminRoleAssignmentResponse> list(Long actorUserId, Long adminUserId) {
        authorizationService.requirePermission(actorUserId, AdminPermission.ADMIN_ROLE_MANAGE);
        requireAdminTarget(adminUserId);
        return assignmentRepository.findAllByAdminUserIdOrderByAssignedAtDescIdDesc(adminUserId).stream()
                .map(AdminRoleAssignmentResponse::from)
                .toList();
    }

    @Transactional
    public AdminRoleAssignmentResponse assign(
            Long actorUserId,
            Long adminUserId,
            AdminRoleAssignmentRequest request
    ) {
        authorizationService.requirePermission(actorUserId, AdminPermission.ADMIN_ROLE_MANAGE);
        User target = requireAdminTarget(adminUserId);
        if (request == null || request.role() == null) {
            throw new AdminException(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_INVALID);
        }
        if (assignmentRepository.findByAdminUserIdAndRoleAndStatus(
                adminUserId, request.role(), AdminRoleAssignmentStatus.ACTIVE).isPresent()) {
            throw new AdminException(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        AdminRoleAssignment assignment = assignmentRepository.save(
                AdminRoleAssignment.assign(adminUserId, request.role(), actorUserId, now)
        );
        AdminRoleAssignmentResponse response = AdminRoleAssignmentResponse.from(assignment);
        auditLogService.record(
                actorUserId,
                AdminAuditAction.ADMIN_ROLE_ASSIGNED,
                AdminAuditTargetType.USER,
                adminUserId,
                request.reason(),
                null,
                Map.of("role", request.role(), "permissions", request.role().permissions())
        );
        eventPublisher.publishEvent(new AdminRoleAssignmentChangedEvent(
                actorUserId, target.getId(), request.role(), AdminRoleAssignmentStatus.ACTIVE, now
        ));
        return response;
    }

    @Transactional
    public AdminRoleAssignmentResponse revoke(
            Long actorUserId,
            Long adminUserId,
            AdminRole role,
            String reason
    ) {
        authorizationService.requirePermission(actorUserId, AdminPermission.ADMIN_ROLE_MANAGE);
        User target = requireAdminTarget(adminUserId);
        if (role == null) {
            throw new AdminException(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_INVALID);
        }
        AdminRoleAssignment assignment = assignmentRepository
                .findByAdminUserIdAndRoleAndStatus(adminUserId, role, AdminRoleAssignmentStatus.ACTIVE)
                .orElseThrow(() -> new AdminException(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        assignment.revoke(now);
        AdminRoleAssignmentResponse response = AdminRoleAssignmentResponse.from(assignmentRepository.save(assignment));
        auditLogService.record(
                actorUserId,
                AdminAuditAction.ADMIN_ROLE_REVOKED,
                AdminAuditTargetType.USER,
                adminUserId,
                reason,
                Map.of("role", role, "status", AdminRoleAssignmentStatus.ACTIVE),
                Map.of("role", role, "status", AdminRoleAssignmentStatus.REVOKED)
        );
        eventPublisher.publishEvent(new AdminRoleAssignmentChangedEvent(
                actorUserId, target.getId(), role, AdminRoleAssignmentStatus.REVOKED, now
        ));
        return response;
    }

    private User requireAdminTarget(Long adminUserId) {
        User target = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.ADMIN_TARGET_USER_NOT_FOUND));
        if (!target.isAdmin()) {
            throw new AdminException(AdminErrorCode.ADMIN_ROLE_ASSIGNMENT_INVALID);
        }
        return target;
    }
}
