package com.typenull.pingdom.privacy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "privacy.processing-history", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PrivacyProcessingHistoryCleanupWorker {

    private final PrivacyProcessingHistoryCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${privacy.processing-history.cleanup-delay:PT24H}",
            initialDelayString = "${privacy.processing-history.cleanup-initial-delay:PT1H}"
    )
    public void cleanupExpiredHistories() {
        try {
            int deletedCount = cleanupService.cleanupExpiredHistories();
            if (deletedCount > 0) {
                log.info("보관 기간이 지난 개인정보 처리 이력을 정리했습니다. deletedCount={}", deletedCount);
            }
        } catch (Exception exception) {
            log.error("개인정보 처리 이력 정리가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
