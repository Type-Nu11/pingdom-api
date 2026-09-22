package com.typenull.pingdom.moderation.application.service.trust;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeReason;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreBatchResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체 신고자 정책을 한 트랜잭션으로 읽어 100 + 승인수×5 - 허위신고수×20을 0~100으로 보정합니다.
 * 점수가 바뀐 정책에만 변경 이력을 남기며, 만료 제한 해제·이상 징후 탐지·개입 규칙 평가는 포함하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class TrustScoreBatchService {

    private final ReporterModerationPolicyRepository policyRepository;
    private final TrustScoreChangeHistoryRepository historyRepository;
    private final Clock clock;

    /**
     * 전체 신고자 정책의 승인·허위 신고 누적 수로 점수를 다시 계산하고 0~100 범위로 보정합니다.
     * 점수가 달라진 정책만 변경 이력을 남기며 전체 재계산은 하나의 트랜잭션입니다. 응답은 별도 전체 건수 조회와 실제 변경 수입니다.
     */
    @Transactional
    public AdminTrustScoreBatchResponse recalculate() {
        int changed = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (ReporterModerationPolicy policy : policyRepository.findAll()) {
            int before = policy.getTrustScore();
            int after = Math.max(0, Math.min(100,
                    100 + (int) policy.getAcceptedCount() * 5 - (int) policy.getFalseReportCount() * 20));
            if (before == after) {
                continue;
            }
            policy.changeTrustScore(after);
            historyRepository.save(TrustScoreChangeHistory.builder()
                    .reporterUserId(policy.getReporterUserId()).beforeScore(before).afterScore(after)
                    .reason(TrustScoreChangeReason.BATCH_RECALCULATION).changedAt(now).build());
            changed++;
        }
        return new AdminTrustScoreBatchResponse((int) policyRepository.count(), changed);
    }

    /**
     * 해당 신고자의 점수 변경 이력을 변경 시각·ID 내림차순으로 조회합니다. page는 1 이상·limit는 1~100으로 보정합니다.
     */
    @Transactional(readOnly = true)
    public AdminTrustScoreChangeHistoryResponse listHistory(Long reporterUserId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var result = historyRepository.findAllByReporterUserId(reporterUserId,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("changedAt").descending().and(Sort.by("id").descending())));
        return new AdminTrustScoreChangeHistoryResponse(
                result.getContent().stream().map(AdminTrustScoreChangeHistoryItem::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
}
