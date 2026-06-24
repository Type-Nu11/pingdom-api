package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.audit.AdminAuditLogItem;
import com.typenull.pingdom.moderation.api.dto.audit.AdminAuditLogResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditLogQueryServiceImpl implements AdminAuditLogQueryService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminAuditLogResponse listAuditLogs(
            Long actorUserId,
            AdminAuditAction action,
            AdminAuditTargetType targetType,
            String targetId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    ) {
        validatePeriod(from, to);

        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<AdminAuditLog> auditLogPage = adminAuditLogRepository.findByFilters(
                actorUserId,
                action,
                targetType,
                normalizeTargetId(targetId),
                from,
                to,
                pageable
        );
        List<AdminAuditLogItem> auditLogs = auditLogPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return AdminAuditLogResponse.of(
                auditLogs,
                safePage,
                safeLimit,
                auditLogPage.getTotalElements(),
                auditLogPage.getTotalPages()
        );
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_AUDIT_LOG_FILTER_PERIOD);
        }
    }

    private String normalizeTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        return targetId.trim();
    }

    private AdminAuditLogItem toItem(AdminAuditLog log) {
        return new AdminAuditLogItem(
                log.getId(),
                log.getActorUserId(),
                log.getActorUsername(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getReason(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getRequestId(),
                log.getCreatedAt()
        );
    }
}
