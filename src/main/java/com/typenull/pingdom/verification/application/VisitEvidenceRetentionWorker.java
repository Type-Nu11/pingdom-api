package com.typenull.pingdom.verification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "verification.visit-evidence", name = "cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceRetentionWorker {
    private final VisitEvidenceRetentionService retentionService;

    @Scheduled(fixedDelayString = "${verification.visit-evidence.cleanup-delay:PT24H}",
            initialDelayString = "${verification.visit-evidence.cleanup-initial-delay:PT1H}")
    public void purgeExpiredEvidence() {
        try {
            retentionService.purgeExpiredEvidence();
        } catch (Exception exception) {
            log.error("방문 인증 증빙 정리 배치가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
