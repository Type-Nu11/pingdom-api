package com.typenull.pingdom.privacy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간 만료 이력이 더 없을 때까지 별도 서비스의 배치 삭제 트랜잭션을 반복 호출합니다.
 * 중간 실패 시 이전 배치의 삭제는 유지되며 이번 실행을 중단한 뒤 다음 스케줄에서 남은 이력을 다시 처리합니다.
 */
@Component
@ConditionalOnProperty(prefix = "privacy.processing-history", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PrivacyProcessingHistoryCleanupWorker {

    private final PrivacyProcessingHistoryCleanupService cleanupService;

    /**
     * 삭제할 이력이 없을 때까지 배치 서비스를 반복 호출하고 완료 건수를 기록한다.
     * 배치 사이에는 트랜잭션이 공유되지 않으며 중간 예외는 로그에 남긴 뒤 이번 실행을 종료해 다음 스케줄에서 이어 처리한다.
     */
    @Scheduled(
            fixedDelayString = "${privacy.processing-history.cleanup-delay:PT24H}",
            initialDelayString = "${privacy.processing-history.cleanup-initial-delay:PT1H}"
    )
    public void cleanupExpiredHistories() {
        try {
            int totalDeletedCount = 0;
            int deletedCount;
            do {
                deletedCount = cleanupService.cleanupExpiredHistories();
                totalDeletedCount += deletedCount;
            } while (deletedCount > 0);

            if (totalDeletedCount > 0) {
                log.info("보관 기간이 지난 개인정보 처리 이력을 정리했습니다. deletedCount={}", totalDeletedCount);
            }
        } catch (Exception exception) {
            log.error("개인정보 처리 이력 정리가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
