package com.typenull.pingdom.moderation.application.service.trust;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeReason;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 한 정책의 최신 통계·점수와 변경 이력을 같은 짧은 트랜잭션에서 처리합니다. */
@Service
@RequiredArgsConstructor
public class TrustScoreRecalculationService {

    private final ReporterModerationPolicyRepository policyRepository;
    private final TrustScoreChangeHistoryRepository historyRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecalculationResult recalculate(Long reporterUserId) {
        ReporterModerationPolicy policy = policyRepository.findByReporterUserIdForUpdate(reporterUserId)
                .orElse(null);
        if (policy == null) {
            return new RecalculationResult(false, false);
        }
        int before = policy.getTrustScore();
        int after = Math.max(0, Math.min(100,
                100 + (int) policy.getAcceptedCount() * 5 - (int) policy.getFalseReportCount() * 20));
        if (before == after) {
            return new RecalculationResult(true, false);
        }
        policy.changeTrustScore(after);
        historyRepository.save(TrustScoreChangeHistory.builder()
                .reporterUserId(reporterUserId)
                .beforeScore(before)
                .afterScore(after)
                .reason(TrustScoreChangeReason.BATCH_RECALCULATION)
                .changedAt(LocalDateTime.now(clock))
                .build());
        return new RecalculationResult(true, true);
    }

    public record RecalculationResult(boolean processed, boolean changed) {
    }
}
