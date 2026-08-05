package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "관리자 역할 할당 응답")
public record AdminRoleAssignmentResponse(
        Long id,
        Long adminUserId,
        AdminRole role,
        AdminRoleAssignmentStatus status,
        Long assignedByUserId,
        LocalDateTime assignedAt,
        LocalDateTime revokedAt,
        List<AdminPermission> permissions
) {

    public static AdminRoleAssignmentResponse from(AdminRoleAssignment assignment) {
        return new AdminRoleAssignmentResponse(
                assignment.getId(),
                assignment.getAdminUserId(),
                assignment.getRole(),
                assignment.getStatus(),
                assignment.getAssignedByUserId(),
                assignment.getAssignedAt(),
                assignment.getRevokedAt(),
                assignment.getRole().permissions().stream().toList()
        );
    }
}
