package com.typenull.pingdom.shared.outbox.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 오래된 선점을 복구하고 준비 이벤트를 전용 executor에 전달.
 * 성공 이력 정리는 별도 주기로 실행되며 프로세스 중단 시 진행 이벤트는 timeout 복구에 위임.
 */
@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxEventWorker {

    private final OutboxEventClaimService claimService;
    private final OutboxEventProcessor processor;
    private final OutboxEventCleanupService cleanupService;
    private final OutboxEventStateService stateService;
    private final TaskExecutor outboxExecutor;

    public OutboxEventWorker(
            OutboxEventClaimService claimService,
            OutboxEventProcessor processor,
            OutboxEventCleanupService cleanupService,
            OutboxEventStateService stateService,
            @Qualifier("outboxExecutor") TaskExecutor outboxExecutor
    ) {
        this.claimService = claimService;
        this.processor = processor;
        this.cleanupService = cleanupService;
        this.stateService = stateService;
        this.outboxExecutor = outboxExecutor;
    }

    /** 이전 실행 종료 후 기본 5초 간격으로 복구를 먼저 수행하고 새 배치를 선점. */
    @Scheduled(
            fixedDelayString = "${outbox.poll-delay:PT5S}",
            initialDelayString = "${outbox.initial-delay:PT5S}"
    )
    public void processReadyEvents() {
        int recoveredCount = claimService.recoverStaleEvents();
        if (recoveredCount > 0) {
            log.warn("고착된 Outbox 이벤트를 복구했습니다. recoveredCount={}", recoveredCount);
        }
        claimService.claimReadyEvents().forEach(this::submit);
    }

    /** 기본 최초 1시간 뒤 시작해 24시간 간격으로 성공 이력 한 배치를 정리. */
    @Scheduled(
            fixedDelayString = "${outbox.cleanup-delay:PT24H}",
            initialDelayString = "${outbox.cleanup-initial-delay:PT1H}"
    )
    public void cleanupSucceededEvents() {
        int deletedCount = cleanupService.cleanupSucceededEvents();
        if (deletedCount > 0) {
            log.info("보관 기간이 지난 Outbox 이벤트를 정리했습니다. deletedCount={}", deletedCount);
        }
    }

    /**
     * processor 밖으로 나온 예외를 기록하고 executor 작업 실패의 다른 작업 전파를 차단.
     * 이 catch의 처리 범위에서 이벤트 상태 갱신 제외.
     */
    private void processSafely(String eventId) {
        try {
            processor.process(eventId);
        } catch (Exception exception) {
            log.error("Outbox Worker 처리 중 예기치 않은 오류가 발생했습니다. eventId={}", eventId, exception);
        }
    }

    /** 선점된 ID를 executor에 제출. 제출이 거절되면 실패 횟수를 반영해 재시도 또는 최종 실패 상태로 전환. */
    private void submit(String eventId) {
        try {
            outboxExecutor.execute(() -> processSafely(eventId));
        } catch (TaskRejectedException exception) {
            stateService.markFailed(eventId, exception);
            log.warn("Outbox Executor 큐가 포화되어 재시도 대상으로 전환했습니다. eventId={}", eventId);
        }
    }
}
