package com.typenull.pingdom.verification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 설정으로 끌 수 있는 증빙 만료 정리 스케줄러.
 * 기본적으로 활성화되며 최초 1시간 뒤 시작하고 이전 실행 종료 후 24시간 간격으로 호출.
 */
@Component
@ConditionalOnProperty(prefix = "verification.visit-evidence", name = "cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceRetentionWorker {
    private final VisitEvidenceRetentionService retentionService;

    /** 트랜잭션 서비스에 정리를 위임하고 실패를 기록. 즉시 반복하지 않고 다음 예약 실행에 위임. */
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
