package com.typenull.pingdom.identity.application.service.admin;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoleAuthorizationService {

    private final UserRepository userRepository;
    private final AdminRoleAssignmentRepository assignmentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void requirePermission(Long actorUserId, AdminPermission permission) {
        if (actorUserId == null || permission == null) {
            throw new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED);
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!actor.isAdmin() || actor.isWithdrawn() || actor.isCurrentlyBanned(now)) {
            throw new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED);
        }

        boolean allowed = assignmentRepository
                .findAllByAdminUserIdAndStatus(actorUserId, AdminRoleAssignmentStatus.ACTIVE)
                .stream()
                .anyMatch(assignment -> assignment.allows(permission));
        if (!allowed) {
            throw new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED);
        }
    }
}
