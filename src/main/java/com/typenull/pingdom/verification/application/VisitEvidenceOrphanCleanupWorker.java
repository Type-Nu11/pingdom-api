package com.typenull.pingdom.verification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "verification.visit-evidence-orphan", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceOrphanCleanupWorker {
    private final VisitEvidenceUploadIntentService intents;

    @Scheduled(fixedDelayString = "${verification.visit-evidence-orphan.cleanup-delay:PT1H}",
            initialDelayString = "${verification.visit-evidence-orphan.cleanup-initial-delay:PT1H}")
    public void queueCleanup() {
        try { intents.queueDueCleanup(); }
        catch (RuntimeException exception) { log.error("방문 증빙 orphan 정리 예약에 실패했습니다.", exception); }
    }
}
