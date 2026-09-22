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

/** 최종 실패 outbox를 다시 처리할 상태로 바꾸고 같은 트랜잭션에 관리자 감사 기록을 남깁니다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOutboxEventRecoveryService {

    private static final int MAX_REASON_LENGTH = 500;

    private final AdminRoleAuthorizationService authorizationService;
    private final OutboxEventStateService outboxEventStateService;
    private final AdminAuditLogService adminAuditLogService;
    private final OutboxMetrics outboxMetrics;

    /**
     * OUTBOX_RECOVERY 권한과 사유를 확인하고 잠긴 FAILED 이벤트를 재시도 가능 상태로 전환합니다.
     * 반환은 예약 상태 변경 결과이며 핸들러의 즉시 실행이나 최종 성공을 의미하지 않습니다.
     */
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

    // 감사 로그에 사용할 재시도 사유의 존재 여부와 최대 길이를 검증합니다.
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
        // 이벤트 처리 전후 상태에서 감사 기록용 불변 스냅샷을 생성합니다.
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
