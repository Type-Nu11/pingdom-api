package com.typenull.pingdom.shared.outbox.application;

import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxEventWorker {

    private final OutboxEventClaimService claimService;
    private final OutboxEventProcessor processor;
    private final OutboxEventCleanupService cleanupService;
    private final OutboxEventStateService stateService;
    private final TaskExecutor outboxExecutor;
    private final Semaphore slots;

    public OutboxEventWorker(OutboxEventClaimService claimService, OutboxEventProcessor processor,
            OutboxEventCleanupService cleanupService, OutboxEventStateService stateService,
            @Qualifier("outboxExecutor") TaskExecutor outboxExecutor, OutboxProperties properties) {
        this.claimService = claimService;
        this.processor = processor;
        this.cleanupService = cleanupService;
        this.stateService = stateService;
        this.outboxExecutor = outboxExecutor;
        this.slots = new Semaphore(properties.workerConcurrency());
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay:PT5S}", initialDelayString = "${outbox.initial-delay:PT5S}")
    public void processReadyEvents() {
        int recoveredCount = claimService.recoverStaleEvents();
        if (recoveredCount > 0) {
            log.warn("고착된 Outbox 이벤트를 복구했습니다. recoveredCount={}", recoveredCount);
        }
        int reserved = 0;
        while (slots.tryAcquire()) {
            reserved++;
        }
        if (reserved == 0) {
            return;
        }
        List<OutboxClaim> claims;
        try {
            claims = claimService.claimReadyEvents(reserved);
        } catch (RuntimeException exception) {
            slots.release(reserved);
            throw exception;
        }
        slots.release(reserved - claims.size());
        claims.forEach(this::submit);
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-delay:PT24H}",
            initialDelayString = "${outbox.cleanup-initial-delay:PT1H}")
    public void cleanupSucceededEvents() {
        int deletedCount = cleanupService.cleanupSucceededEvents();
        if (deletedCount > 0) {
            log.info("보관 기간이 지난 Outbox 이벤트를 정리했습니다. deletedCount={}", deletedCount);
        }
    }

    private void processSafely(OutboxClaim claim) {
        try {
            processor.process(claim);
        } catch (Exception exception) {
            log.error("Outbox Worker 처리 오류. eventId={}", claim.eventId(), exception);
        } finally {
            slots.release();
        }
    }

    private void submit(OutboxClaim claim) {
        try {
            outboxExecutor.execute(() -> processSafely(claim));
        } catch (RuntimeException exception) {
            try {
                stateService.releaseClaim(claim);
                log.warn("Outbox 제출 거절. 업무 실패 횟수 없이 선점을 반환합니다. eventId={}", claim.eventId());
            } catch (RuntimeException releaseFailure) {
                log.error("Outbox 선점 반환 실패. lease 만료 후 복구됩니다. eventId={}",
                        claim.eventId(), releaseFailure);
            } finally {
                slots.release();
            }
        }
    }
}
