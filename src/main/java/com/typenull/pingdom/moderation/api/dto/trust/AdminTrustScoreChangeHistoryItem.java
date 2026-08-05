package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import java.time.LocalDateTime;

public record AdminTrustScoreChangeHistoryItem(
        Long id,
        Long reporterUserId,
        int beforeScore,
        int afterScore,
        String reason,
        LocalDateTime changedAt
) {
    public static AdminTrustScoreChangeHistoryItem from(TrustScoreChangeHistory history) {
        return new AdminTrustScoreChangeHistoryItem(
                history.getId(), history.getReporterUserId(), history.getBeforeScore(),
                history.getAfterScore(), history.getReason().name(), history.getChangedAt()
        );
    }
}
