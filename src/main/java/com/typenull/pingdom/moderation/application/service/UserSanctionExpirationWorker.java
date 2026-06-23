package com.typenull.pingdom.moderation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "user.sanction", name = "expiration-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class UserSanctionExpirationWorker {

    private final UserSanctionCommandService userSanctionCommandService;
    private final Clock clock;

    @Value("${user.sanction.expiration-batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${user.sanction.expiration-delay:PT1H}",
            initialDelayString = "${user.sanction.expiration-initial-delay:PT5M}"
    )
    public void expireTemporaryBans() {
        try {
            int expiredCount = userSanctionCommandService.expireExpiredTemporaryBans(LocalDateTime.now(clock), batchSize);
            if (expiredCount > 0) {
                log.info("만료된 기간 제재 정리를 완료했습니다. expiredCount={}", expiredCount);
            }
        } catch (Exception exception) {
            log.error("만료된 기간 제재 정리가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
