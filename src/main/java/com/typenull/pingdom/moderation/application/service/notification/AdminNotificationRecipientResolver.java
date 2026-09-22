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

/**
 * 알림 유형별 업무 권한을 허용하는 활성 역할 할당에서 관리자 수신자 조회.
 * 탈퇴·현재 정지 계정과 역할 할당 없는 ADMIN 계정은 제외하고 사용자 ID를 중복 제거해 정렬.
 */
@Component
@RequiredArgsConstructor
public class AdminNotificationRecipientResolver {

    private final AdminRoleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 알림 종류에 필요한 업무 권한을 허용하는 활성 역할 할당을 찾고 현재 ADMIN인 미탈퇴·미정지 계정 ID를 정렬해 반환.
     * 역할이 여러 개여도 수신자는 중복 제거하며 지원하지 않는 알림 종류는 IllegalArgumentException으로 거절.
     */
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
