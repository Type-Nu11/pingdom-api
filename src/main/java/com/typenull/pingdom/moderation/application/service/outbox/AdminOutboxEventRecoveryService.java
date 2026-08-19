package com.typenull.pingdom.moderation.application.service.outbox;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventItem;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.ManualRetryResult;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventOperationSnapshot;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
/** 관리자 요청에 따른 outbox 이벤트 재처리와 재시도 이력 기록을 담당합니다. */
public class AdminOutboxEventRecoveryService {

    private static final int MAX_REASON_LENGTH = 500;

    private final AdminRoleAuthorizationService authorizationService;
    private final OutboxEventStateService outboxEventStateService;
    private final AdminAuditLogService adminAuditLogService;
    private final OutboxMetrics outboxMetrics;

    @Transactional
    public AdminOutboxEventItem retry(Long adminUserId, String eventId, String reason) {
        authorizationService.requirePermission(adminUserId, AdminPermission.OUTBOX_RECOVERY);
        String normalizedReason = normalizeReason(reason);

        ManualRetryResult result = outboxEventStateService.retryFailedEvent(eventId);
        if (result.outcome() == OutboxEventStateService.ManualRetryOutcome.NOT_FOUND) {
            outboxMetrics.recordManualRetry(null, "not_found");
            throw new AdminException(AdminErrorCode.OUTBOX_EVENT_NOT_FOUND);
        }
        if (result.outcome() == OutboxEventStateService.ManualRetryOutcome.NOT_RETRYABLE) {
            outboxMetrics.recordManualRetry(result.before().eventType(), "not_retryable");
            log.warn(
                    "Outbox 수동 재처리 요청을 거절했습니다. adminUserId={}, eventId={}, status={}",
                    adminUserId,
                    eventId,
                    result.before().status()
            );
            throw new AdminException(AdminErrorCode.OUTBOX_EVENT_RETRY_NOT_ALLOWED);
        }

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.OUTBOX_EVENT_RETRIED,
                AdminAuditTargetType.OUTBOX_EVENT,
                eventId,
                normalizedReason,
                RetryAuditState.from(result.before()),
                RetryAuditState.from(result.after())
        );
        outboxMetrics.recordManualRetry(result.after().eventType(), "success");
        log.info(
                "Outbox 이벤트를 수동 재처리 대상으로 전환했습니다. adminUserId={}, eventId={}, previousAttemptCount={}",
                adminUserId,
                eventId,
                result.before().attemptCount()
        );
        return AdminOutboxEventItem.from(result.after());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new AdminException(AdminErrorCode.OUTBOX_EVENT_RETRY_REASON_REQUIRED);
        }
        return reason.trim();
    }

    private record RetryAuditState(
            com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus status,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            LocalDateTime updatedAt
    ) {
        private static RetryAuditState from(OutboxEventOperationSnapshot event) {
            return new RetryAuditState(
                    event.status(),
                    event.attemptCount(),
                    event.nextAttemptAt(),
                    event.lastError(),
                    event.updatedAt()
            );
        }
    }
}
