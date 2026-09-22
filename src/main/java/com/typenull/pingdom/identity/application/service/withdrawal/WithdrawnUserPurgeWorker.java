package com.typenull.pingdom.identity.application.service.withdrawal;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 활성화된 스케줄마다 탈퇴 회원 정리 배치를 한 번 호출.
 * 배치 예외를 기록하고 다음 스케줄로 재시도를 넘기며 한 실행에서는 일부 배치만 처리.
 */
@Component
@ConditionalOnProperty(prefix = "user.withdrawal", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class WithdrawnUserPurgeWorker {

    private final WithdrawnUserPurgeService purgeService;
    private final Clock clock;

    /**
     * 현재 Clock 시각으로 탈퇴 회원 최종 삭제를 한 배치 호출하고 삭제가 있을 때 건수를 기록.
     * 예외는 로그에 남기고 종료하며 남은 대상과 실패 재시도는 다음 스케줄로 전달.
     */
    @Scheduled(
            fixedDelayString = "${user.withdrawal.cleanup-delay:PT24H}",
            initialDelayString = "${user.withdrawal.cleanup-initial-delay:PT1H}"
    )
    public void purgeExpiredUsers() {
        try {
            int purgedCount = purgeService.purgeExpiredUsers(LocalDateTime.now(clock));
            if (purgedCount > 0) {
                log.info("탈퇴 사용자 최종 삭제 배치를 완료했습니다. purgedCount={}", purgedCount);
            }
        } catch (Exception exception) {
            log.error("탈퇴 사용자 최종 삭제 배치가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
