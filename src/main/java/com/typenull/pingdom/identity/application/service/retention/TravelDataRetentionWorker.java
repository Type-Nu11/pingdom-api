package com.typenull.pingdom.identity.application.service.retention;

import com.typenull.pingdom.shared.observability.TravelDataRetentionMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 여행 데이터 정리를 주기적으로 실행하고 삭제 건수 또는 실패를 지표에 기록합니다.
 * 실패는 로그에 남겨 다음 스케줄에서 다시 처리하도록 합니다.
 */
@Component
@ConditionalOnProperty(prefix = "travel.data-retention", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TravelDataRetentionWorker {

    private final TravelDataRetentionService travelDataRetentionService;
    private final TravelDataRetentionMetrics travelDataRetentionMetrics;

    /**
     * 정해진 스케줄마다 여행 데이터 정리를 한 번 호출하고 총 삭제 건수를 성공 지표에 기록합니다.
     * 실패는 지표·로그에 남기고 호출 밖으로 전파하지 않아 후속 스케줄에서 다시 시도하도록 합니다.
     */
    @Scheduled(
            fixedDelayString = "${travel.data-retention.cleanup-delay:PT1H}",
            initialDelayString = "${travel.data-retention.cleanup-initial-delay:PT1H}"
    )
    public void purgeExpiredData() {
        try {
            int deletedCount = travelDataRetentionService.purgeExpiredData().totalDeletedCount();
            travelDataRetentionMetrics.recordSuccess(deletedCount);
            if (deletedCount > 0) {
                log.info("여행 데이터 정리 배치를 완료했습니다. deletedCount={}", deletedCount);
            }
        } catch (Exception exception) {
            travelDataRetentionMetrics.recordFailure(exception);
            log.error("여행 데이터 정리 배치가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
