package com.typenull.pingdom.moderation.application.service.trust;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeReason;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreBatchResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryItem;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustScoreBatchService {

    private final ReporterModerationPolicyRepository policyRepository;
    private final TrustScoreChangeHistoryRepository historyRepository;
    private final Clock clock;

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

    @Transactional(readOnly = true)
    public List<AdminTrustScoreChangeHistoryItem> listHistory(Long reporterUserId) {
        return historyRepository.findTop100ByReporterUserIdOrderByChangedAtDescIdDesc(reporterUserId)
                .stream().map(AdminTrustScoreChangeHistoryItem::from).toList();
    }
}
