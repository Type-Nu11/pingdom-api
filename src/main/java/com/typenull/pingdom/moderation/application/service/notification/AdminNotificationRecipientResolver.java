package com.typenull.pingdom.moderation.application.service.notification;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminNotificationRecipientResolver {

    private final AdminRoleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> resolve(NotificationType type) {
        AdminPermission permission = requiredPermission(type);
        List<AdminRole> allowedRoles = Arrays.stream(AdminRole.values())
                .filter(role -> role.allows(permission))
                .toList();
        Set<Long> assignedAdminIds = assignmentRepository
                .findAllByRoleInAndStatus(allowedRoles, AdminRoleAssignmentStatus.ACTIVE)
                .stream()
                .map(assignment -> assignment.getAdminUserId())
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now(clock);

        return userRepository.findAllById(assignedAdminIds).stream()
                .filter(User::isAdmin)
                .filter(user -> !user.isWithdrawn())
                .filter(user -> !user.isCurrentlyBanned(now))
                .map(User::getId)
                .sorted()
                .toList();
    }

    private AdminPermission requiredPermission(NotificationType type) {
        return switch (type) {
            case ADMIN_REPORT_RECEIVED, ADMIN_REPORT_PROCESSED -> AdminPermission.REPORT_REVIEW;
            case ADMIN_DUPLICATE_PLACE_DETECTED -> AdminPermission.PLACE_MODERATE;
            case ADMIN_USER_SANCTION -> AdminPermission.USER_SANCTION;
            default -> throw new IllegalArgumentException("관리자 알림 유형만 수신자를 결정할 수 있습니다.");
        };
    }
}
