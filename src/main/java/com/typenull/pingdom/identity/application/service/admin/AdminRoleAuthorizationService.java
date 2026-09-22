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

/**
 * 관리자 계정 상태와 ACTIVE 역할 할당을 함께 조회해 세부 권한을 판정.
 * ADMIN 역할만으로는 접근 불가하며 탈퇴·현재 정지·미할당 상태는 모두 거부.
 */
@Service
@RequiredArgsConstructor
public class AdminRoleAuthorizationService {

    private final UserRepository userRepository;
    private final AdminRoleAssignmentRepository assignmentRepository;
    private final Clock clock;

    /**
     * 요청자가 탈퇴·정지되지 않은 ADMIN이며 ACTIVE 역할 중 하나가 지정 권한을 허용하는지 확인.
     * 사용자·권한 누락, 계정 부재 또는 권한 부족은 모두 ADMIN_PERMISSION_REQUIRED로 거절해 보호 작업 진입을 차단.
     */
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
