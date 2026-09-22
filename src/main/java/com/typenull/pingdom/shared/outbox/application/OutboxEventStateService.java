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
 * 상태 조건과 낙관적 잠금은 있으나 worker별 lease 소유 토큰은 검사 대상에서 제외.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventStateService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final OutboxBackoffPolicy backoffPolicy;
    private final Clock outboxClock;

    /** PROCESSING 상태만 읽기 스냅샷으로 반환하며 없거나 다른 상태이면 null. */
    @Transactional(readOnly = true)
    public OutboxEventSnapshot findProcessingEvent(String eventId) {
        return outboxEventRepository.findById(eventId)
                .filter(event -> event.getStatus() == OutboxEventStatus.PROCESSING)
                .map(OutboxEventSnapshot::from)
                .orElse(null);
    }

    /** 현재 행이 있으면 성공 전이를 요청. 도메인은 PROCESSING 외 상태의 호출을 무시. */
    @Transactional
    public void markSucceeded(String eventId) {
        outboxEventRepository.findById(eventId)
                .ifPresent(event -> event.succeed(LocalDateTime.now(outboxClock)));
    }

    /**
     * 현재 PROCESSING인 이벤트만 실패 횟수와 다음 시각을 갱신.
     * 행이 없거나 다른 상태이면 null을 반환하고 기존 실패 내용 유지.
     */
    @Transactional
    public OutboxEventStatus markFailed(String eventId, Throwable failure) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.PROCESSING) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(outboxClock);
        event.fail(
                now,
                properties.maxAttempts(),
                now.plus(backoffPolicy.calculateDelay(event.getAttemptCount() + 1)),
                failureMessage(failure)
        );
        return event.getStatus();
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
