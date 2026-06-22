package com.typenull.pingdom.shared.outbox.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxEventWorker {

    private final OutboxEventClaimService claimService;
    private final OutboxEventProcessor processor;
    private final OutboxEventCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${outbox.poll-delay:PT5S}",
            initialDelayString = "${outbox.initial-delay:PT5S}"
    )
    public void processReadyEvents() {
        int recoveredCount = claimService.recoverStaleEvents();
        if (recoveredCount > 0) {
            log.warn("고착된 Outbox 이벤트를 복구했습니다. recoveredCount={}", recoveredCount);
        }
        claimService.claimReadyEvents().forEach(this::processSafely);
    }

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

    private void processSafely(String eventId) {
        try {
            processor.process(eventId);
        } catch (Exception exception) {
            log.error("Outbox Worker 처리 중 예기치 않은 오류가 발생했습니다. eventId={}", eventId, exception);
        }
    }
}
