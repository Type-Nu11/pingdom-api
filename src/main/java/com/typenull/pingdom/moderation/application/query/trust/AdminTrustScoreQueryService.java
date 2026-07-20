package com.typenull.pingdom.moderation.application.query.trust;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreGrade;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreEvidenceResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminTrustScoreQueryService {

    private static final int BASE_SCORE = 100;
    private static final int ACCEPTED_REPORT_BONUS = 5;
    private static final int FALSE_REPORT_PENALTY = 20;

    private final ReporterModerationPolicyRepository reporterModerationPolicyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminTrustScoreResponse getTrustScore(Long reporterUserId) {
        ReporterModerationPolicy policy = reporterModerationPolicyRepository.findById(reporterUserId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.TRUST_SCORE_REPORTER_POLICY_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        return new AdminTrustScoreResponse(
                policy.getReporterUserId(),
                policy.getReporterUsername(),
                policy.getTrustScore(),
                TrustScoreGrade.fromScore(policy.getTrustScore()),
                policy.isRestricted(now),
                policy.getRestrictedUntil(),
                policy.getRestrictionReason(),
                evidence(policy)
        );
    }

    private AdminTrustScoreEvidenceResponse evidence(ReporterModerationPolicy policy) {
        long acceptedScoreBonus = policy.getAcceptedCount() * ACCEPTED_REPORT_BONUS;
        long falseReportScorePenalty = policy.getFalseReportCount() * FALSE_REPORT_PENALTY;
        return new AdminTrustScoreEvidenceResponse(
                policy.getSubmittedCount(),
                policy.getAcceptedCount(),
                policy.getDeclinedCount(),
                policy.getFalseReportCount(),
                acceptanceRate(policy),
                BASE_SCORE,
                acceptedScoreBonus,
                falseReportScorePenalty
        );
    }

    private double acceptanceRate(ReporterModerationPolicy policy) {
        if (policy.getSubmittedCount() == 0) {
            return 0.0d;
        }
        return BigDecimal.valueOf(policy.getAcceptedCount() * 100.0d / policy.getSubmittedCount())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
