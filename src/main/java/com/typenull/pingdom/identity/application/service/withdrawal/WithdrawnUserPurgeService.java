package com.typenull.pingdom.identity.application.service.withdrawal;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository.PurgeCandidate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 후보 조회만 조정하고 사용자별 작업은 별도 트랜잭션으로 실행한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawnUserPurgeService {

    private final UserRepository userRepository;
    private final UserWithdrawalProperties properties;
    private final WithdrawnUserPurgeTransactionService transactionService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int purgeExpiredUsers(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(properties.retention());
        LocalDateTime neverAttemptedAt = LocalDateTime.of(1, 1, 1, 0, 0);
        LocalDateTime afterAttemptAt = neverAttemptedAt;
        LocalDateTime afterAt = neverAttemptedAt;
        long afterId = 0;
        int deleted = 0;
        int deferred = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.maxCleanupBatches(); batch++) {
            List<PurgeCandidate> candidates = userRepository.findPurgeCandidates(UserStatus.WITHDRAWN, cutoff, now,
                    neverAttemptedAt, afterAttemptAt, afterAt, afterId, PageRequest.of(0, properties.cleanupBatchSize()));
            if (candidates.isEmpty()) {
                break;
            }
            // 처리 중 FK/감사 오류가 나도 마지막 후보 다음부터 진행한다.
            for (PurgeCandidate candidate : candidates) {
                afterAttemptAt = candidate.getAttemptedAt();
                afterAt = candidate.getWithdrawnAt();
                afterId = candidate.getUserId();
                try {
                    switch (transactionService.purge(candidate.getUserId(), cutoff, now)) {
                        case DELETED -> deleted++;
                        case DEFERRED -> deferred++;
                        case SKIPPED -> { }
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("탈퇴 사용자 최종 삭제를 보류합니다. userId={}, reasonCode=PROCESSING_FAILED",
                            candidate.getUserId());
                    try {
                        if (!transactionService.recordFailure(candidate.getUserId(), cutoff, now)) {
                            log.warn("탈퇴 사용자 삭제 재시도 상태를 기록하지 못했습니다. userId={}", candidate.getUserId());
                        }
                    } catch (RuntimeException retryException) {
                        log.error("탈퇴 사용자 삭제 재시도 상태 저장에 실패했습니다. userId={}", candidate.getUserId());
                    }
                }
            }
        }
        log.info("탈퇴 사용자 최종 삭제 처리를 마쳤습니다. deleted={}, deferred={}, failed={}", deleted, deferred, failed);
        return deleted;
    }
}
