package com.typenull.pingdom.moderation.application.query.audit;

import com.typenull.pingdom.moderation.api.dto.audit.AdminAuditLogResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.time.LocalDateTime;

public interface AdminAuditLogQueryService {

    AdminAuditLogResponse listAuditLogs(
            Long actorUserId,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            String targetId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    );
}
