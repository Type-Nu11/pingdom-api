package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.infrastructure.VisitVerificationSessionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 체류 인증 세션의 최소 보관 기간이 지나면 서버 판정 이력을 삭제. */
@Component
@RequiredArgsConstructor
public class VisitVerificationRetentionWorker {
    private final VisitVerificationSessionRepository sessionRepository;
    private final VisitVerificationProperties properties;
    private final Clock clock;

    /**
     * 세션 만료 시각에 보관 기간까지 더한 시점이 지난 행을 트랜잭션으로 삭제.
     * 기본 cron은 매일 04시이며 별도 zone 지정이 없어 스케줄러의 시간대를 따름.
     */
    @Scheduled(cron = "${verification.visit-verification.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void deleteExpiredRetention() {
        sessionRepository.deleteByExpiresAtLessThanEqual(clock.instant().minus(properties.retention()));
    }
}
