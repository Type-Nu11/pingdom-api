package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * handler 처리와 분리된 트랜잭션에서 이벤트 상태를 조회·변경하고 수동 재시도 결과를 제공.
 * 토큰과 DB 시각의 유효 lease를 함께 검사하여 이전 선점의 결과를 거절한다.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventStateService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final OutboxBackoffPolicy backoffPolicy;
    private final Clock outboxClock;

    @Transactional(readOnly = true)
    public OutboxEventSnapshot findProcessingEvent(OutboxClaim claim) {
        return outboxEventRepository.findOwnedProcessingEvent(claim.eventId(), claim.claimToken())
                .map(OutboxEventSnapshot::from).orElse(null);
    }

    @Transactional(timeout = 5)
    public boolean markSucceeded(OutboxClaim claim) {
        return ownedForUpdate(claim) != null
                && outboxEventRepository.completeClaim(claim.eventId(), claim.claimToken()) == 1;
    }

    @Transactional(timeout = 5)
    public OutboxEventStatus markFailed(OutboxClaim claim, Throwable failure) {
        OutboxEvent event = ownedForUpdate(claim);
        if (event == null) {
            return null;
        }
        int nextAttempt = event.getAttemptCount() + 1;
        String error = failureMessage(failure);
        int changed = outboxEventRepository.failClaim(claim.eventId(), claim.claimToken(),
                properties.maxAttempts(), backoffPolicy.calculateDelay(nextAttempt).toMillis(),
                error.substring(0, Math.min(error.length(), 2000)));
        return changed == 0 ? null
                : nextAttempt >= properties.maxAttempts() ? OutboxEventStatus.FAILED : OutboxEventStatus.RETRY;
    }

    @Transactional(timeout = 5)
    public boolean renewClaim(OutboxClaim claim) {
        return ownedForUpdate(claim) != null
                && outboxEventRepository.renewClaim(claim.eventId(), claim.claimToken(),
                properties.processingTimeout().toMillis(), properties.maxExecutionTime().toMillis()) == 1;
    }

    @Transactional(timeout = 5)
    public boolean releaseClaim(OutboxClaim claim) {
        return ownedForUpdate(claim) != null
                && outboxEventRepository.releaseClaim(claim.eventId(), claim.claimToken()) == 1;
    }

    private OutboxEvent ownedForUpdate(OutboxClaim claim) {
        OutboxEvent event = outboxEventRepository.findByEventIdForUpdate(claim.eventId()).orElse(null);
        // 잠금 대기 중 lease가 만료될 수 있으므로 잠금을 얻은 후 DB 시각으로 재확인한다.
        if (event == null || event.getStatus() != OutboxEventStatus.PROCESSING
                || !claim.claimToken().equals(event.getClaimToken()) || event.getLeaseUntil() == null
                || !event.getLeaseUntil().isAfter(outboxEventRepository.databaseNow())) {
            return null;
        }
        return event;
    }

    /**
     * 이벤트를 쓰기 잠금으로 읽고 FAILED인 경우에만 횟수를 초기화해 RETRY로 되돌림.
     * 즉시 handler를 실행하지 않으며 다음 worker 선점 대상으로 전환.
     */
    @Transactional
    public ManualRetryResult retryFailedEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findByEventIdForUpdate(eventId).orElse(null);
        if (event == null) {
            return ManualRetryResult.notFound();
        }

        OutboxEventOperationSnapshot before = OutboxEventOperationSnapshot.from(event);
        if (event.getStatus() != OutboxEventStatus.FAILED) {
            return ManualRetryResult.notRetryable(before);
        }

        event.retry(LocalDateTime.now(outboxClock));
        return ManualRetryResult.retried(before, OutboxEventOperationSnapshot.from(event));
    }

    /** 예외 타입과 메시지를 저장용 오류 설명으로 통합. 길이 제한은 도메인이 적용하며 값 마스킹은 미수행. */
    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record OutboxEventSnapshot(
            String eventId,
            com.typenull.pingdom.shared.outbox.domain.OutboxEventType eventType,
            String payload,
            String aggregateType,
            String aggregateId,
            int attemptCount
    ) {
        private static OutboxEventSnapshot from(OutboxEvent event) {
            return new OutboxEventSnapshot(
                    event.getEventId(),
                    event.getEventType(),
                    event.getPayload(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAttemptCount()
            );
        }
    }

    public enum ManualRetryOutcome {
        RETRIED,
        NOT_FOUND,
        NOT_RETRYABLE
    }

    public record ManualRetryResult(
            ManualRetryOutcome outcome,
            OutboxEventOperationSnapshot before,
            OutboxEventOperationSnapshot after
    ) {
        private static ManualRetryResult retried(
                OutboxEventOperationSnapshot before,
                OutboxEventOperationSnapshot after
        ) {
            return new ManualRetryResult(ManualRetryOutcome.RETRIED, before, after);
        }

        private static ManualRetryResult notFound() {
            return new ManualRetryResult(ManualRetryOutcome.NOT_FOUND, null, null);
        }

        private static ManualRetryResult notRetryable(OutboxEventOperationSnapshot snapshot) {
            return new ManualRetryResult(ManualRetryOutcome.NOT_RETRYABLE, snapshot, snapshot);
        }
    }

    public record OutboxEventOperationSnapshot(
            String eventId,
            com.typenull.pingdom.shared.outbox.domain.OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            OutboxEventStatus status,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            LocalDateTime processingStartedAt,
            LocalDateTime processedAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        private static OutboxEventOperationSnapshot from(OutboxEvent event) {
            return new OutboxEventOperationSnapshot(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getStatus(),
                    event.getAttemptCount(),
                    event.getNextAttemptAt(),
                    event.getProcessingStartedAt(),
                    event.getProcessedAt(),
                    event.getLastError(),
                    event.getCreatedAt(),
                    event.getUpdatedAt()
            );
        }
    }
}
