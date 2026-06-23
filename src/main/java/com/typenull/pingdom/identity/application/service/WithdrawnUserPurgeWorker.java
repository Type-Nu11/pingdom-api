package com.typenull.pingdom.identity.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "user.withdrawal", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WithdrawnUserPurgeWorker {

    private static final Logger log = LoggerFactory.getLogger(WithdrawnUserPurgeWorker.class);

    private final WithdrawnUserPurgeService purgeService;
    private final Clock clock;

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
