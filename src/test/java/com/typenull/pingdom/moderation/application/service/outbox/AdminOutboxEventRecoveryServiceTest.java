package com.typenull.pingdom.moderation.application.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.ManualRetryOutcome;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.ManualRetryResult;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventOperationSnapshot;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOutboxEventRecoveryServiceTest {

    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private OutboxEventStateService outboxEventStateService;
    @Mock private AdminAuditLogService adminAuditLogService;
    @Mock private OutboxMetrics outboxMetrics;

    private AdminOutboxEventRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new AdminOutboxEventRecoveryService(
                authorizationService,
                outboxEventStateService,
                adminAuditLogService,
                outboxMetrics
        );
    }

    @Test
    void retriesFailedEventWithPermissionAuditAndMetric() {
        OutboxEventOperationSnapshot before = snapshot(OutboxEventStatus.FAILED, 5, "provider failure");
        OutboxEventOperationSnapshot after = snapshot(OutboxEventStatus.RETRY, 0, null);
        when(outboxEventStateService.retryFailedEvent("event-1"))
                .thenReturn(new ManualRetryResult(ManualRetryOutcome.RETRIED, before, after));

        AdminOutboxEventItem response = service.retry(10L, "event-1", " provider recovered ");

        verify(authorizationService).requirePermission(10L, AdminPermission.OUTBOX_RECOVERY);
        verify(adminAuditLogService).record(
                eq(10L),
                eq(AdminAuditAction.OUTBOX_EVENT_RETRIED),
                eq(AdminAuditTargetType.OUTBOX_EVENT),
                eq("event-1"),
                eq("provider recovered"),
                any(),
                any()
        );
        verify(outboxMetrics).recordManualRetry(OutboxEventType.EMAIL_VERIFICATION_REQUESTED, "success");
        assertThat(response.status()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(response.attemptCount()).isZero();
    }

    @Test
    void rejectsMissingEventWithIdentifiableError() {
        when(outboxEventStateService.retryFailedEvent("missing"))
                .thenReturn(new ManualRetryResult(ManualRetryOutcome.NOT_FOUND, null, null));

        assertThatThrownBy(() -> service.retry(10L, "missing", "checked"))
                .isInstanceOfSatisfying(
                        AdminException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.OUTBOX_EVENT_NOT_FOUND)
                );
        verify(outboxMetrics).recordManualRetry(null, "not_found");
    }

    @Test
    void rejectsEventThatIsNoLongerFailed() {
        OutboxEventOperationSnapshot pending = snapshot(OutboxEventStatus.PENDING, 0, null);
        when(outboxEventStateService.retryFailedEvent("event-1"))
                .thenReturn(new ManualRetryResult(ManualRetryOutcome.NOT_RETRYABLE, pending, pending));

        assertThatThrownBy(() -> service.retry(10L, "event-1", "checked"))
                .isInstanceOfSatisfying(
                        AdminException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.OUTBOX_EVENT_RETRY_NOT_ALLOWED)
                );
        verify(outboxMetrics).recordManualRetry(
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "not_retryable"
        );
    }

    private OutboxEventOperationSnapshot snapshot(OutboxEventStatus status, int attemptCount, String lastError) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 10, 0);
        return new OutboxEventOperationSnapshot(
                "event-1",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "USER",
                "10",
                status,
                attemptCount,
                now,
                null,
                null,
                lastError,
                now.minusHours(1),
                now
        );
    }
}
