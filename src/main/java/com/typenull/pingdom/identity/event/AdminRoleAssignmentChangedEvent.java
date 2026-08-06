package com.typenull.pingdom.identity.event;

import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import java.time.LocalDateTime;

public record AdminRoleAssignmentChangedEvent(
        Long actorUserId,
        Long adminUserId,
        AdminRole role,
        AdminRoleAssignmentStatus status,
        LocalDateTime occurredAt
) {
}
