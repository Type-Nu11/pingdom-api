package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.infrastructure.VisitVerificationSessionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 체류 인증 세션의 최소 보관 기간이 지나면 서버 판정 이력을 삭제합니다. */
@Component
@RequiredArgsConstructor
public class VisitVerificationRetentionWorker {
    private final VisitVerificationSessionRepository sessionRepository;
    private final VisitVerificationProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${verification.visit-verification.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void deleteExpiredRetention() {
        sessionRepository.deleteByExpiresAtLessThanEqual(clock.instant().minus(properties.retention()));
    }
}
