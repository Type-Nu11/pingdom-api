package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalySeverity;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreAnomalyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreInterventionRuleRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
/** 신고자의 빈도·제재·신뢰도 정책을 평가해 신고 가능 여부와 후속 상태를 관리합니다. */
public class ReportPolicyService {

    private static final int AUTO_HIDE_WEIGHT_THRESHOLD = 3;
    private static final int FALSE_REPORT_RESTRICTION_THRESHOLD = 3;
    private static final int RESTRICTION_DAYS = 7;
    private static final int RAPID_DROP_THRESHOLD = 40;
    private static final int LOW_ACCEPTANCE_MIN_SUBMITTED_COUNT = 5;
    private static final double LOW_ACCEPTANCE_RATE_THRESHOLD = 0.3d;
    private static final String AUTO_HIDE_REASON = "REPORT_POLICY_AUTO_HIDE";
    private static final String RESTRICTION_REASON = "FALSE_REPORT_THRESHOLD_EXCEEDED";

    private final ReporterModerationPolicyRepository reporterPolicyRepository;
    private final PostReportRepository postReportRepository;
    private final TrustScoreAnomalyRepository trustScoreAnomalyRepository;
    private final TrustScoreInterventionRuleRepository trustScoreInterventionRuleRepository;
    private final PlaceGrowthService placeGrowthService;
    private final Clock clock;

    public void validateCanReport(Long reporterUserId, LocalDateTime now) {
        reporterPolicyRepository.findById(reporterUserId)
                .ifPresent(policy -> {
                    policy.clearExpiredRestriction(now);
                    if (policy.isRestricted(now)) {
                        throw new MapException(MapErrorCode.REPORTER_RESTRICTED);
                    }
                });
    }

    public void recordSubmitted(Long reporterUserId, String reporterUsername) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        policy.recordSubmitted(reporterUsername);
        reporterPolicyRepository.save(policy);
    }

    public void recordAccepted(Long reporterUserId, String reporterUsername) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        int baselineScore = policy.getTrustScore();
        LocalDateTime now = LocalDateTime.now(clock);
        policy.recordAccepted(reporterUsername);
        detectAnomalies(policy, baselineScore, now);
        applyHighestPriorityRule(policy, now);
        reporterPolicyRepository.save(policy);
    }

    public void recordDeclined(Long reporterUserId, String reporterUsername, LocalDateTime now) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        int baselineScore = policy.getTrustScore();
        policy.recordDeclined(reporterUsername);
        if (policy.getFalseReportCount() >= FALSE_REPORT_RESTRICTION_THRESHOLD) {
            policy.restrictUntil(now.plusDays(RESTRICTION_DAYS), RESTRICTION_REASON);
        }
        detectAnomalies(policy, baselineScore, now);
        applyHighestPriorityRule(policy, now);
        reporterPolicyRepository.save(policy);
    }

    public boolean autoHideIfNeeded(MapImage mapImage, LocalDateTime now) {
        if (mapImage == null || !mapImage.isVisible()) {
            return false;
        }

        List<PostReport> activeReports = postReportRepository.findAllByMapImage_IdAndStatusIn(
                mapImage.getId(),
                List.of(PostReportStatus.PENDING, PostReportStatus.ACCEPTED)
        );
        Map<Long, ReporterModerationPolicy> policiesByReporterId = loadPoliciesByReporterId(activeReports);
        double weightedScore = activeReports.stream()
                .mapToDouble(report -> reporterWeight(policiesByReporterId.get(report.getReporterUserId())))
                .sum();

        if (weightedScore < AUTO_HIDE_WEIGHT_THRESHOLD) {
            return false;
        }

        boolean hidden = mapImage.autoHide(AUTO_HIDE_REASON, now, null);
        decreasePhotoCountIfNeeded(mapImage, hidden);
        return hidden;
    }

    private void decreasePhotoCountIfNeeded(MapImage mapImage, boolean hidden) {
        if (!hidden || mapImage.getMapPlace() == null) {
            return;
        }
        placeGrowthService.decreasePhotoCount(mapImage.getMapPlace().getId());
    }

    private Map<Long, ReporterModerationPolicy> loadPoliciesByReporterId(List<PostReport> activeReports) {
        List<Long> reporterIds = activeReports.stream()
                .map(PostReport::getReporterUserId)
                .distinct()
                .toList();
        return reporterPolicyRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(ReporterModerationPolicy::getReporterUserId, Function.identity()));
    }

    private double reporterWeight(ReporterModerationPolicy policy) {
        if (policy == null) {
            return 1.0;
        }
        if (policy.getTrustScore() >= 80) {
            return 1.0;
        }
        if (policy.getTrustScore() >= 50) {
            return 0.75;
        }
        return 0.5;
    }

    private ReporterModerationPolicy getOrCreate(Long reporterUserId, String reporterUsername) {
        return reporterPolicyRepository.findByReporterUserIdForUpdate(reporterUserId)
                .orElseGet(() -> ReporterModerationPolicy.create(reporterUserId, reporterUsername));
    }

    private void detectAnomalies(ReporterModerationPolicy policy, int baselineScore, LocalDateTime now) {
        if (baselineScore - policy.getTrustScore() >= RAPID_DROP_THRESHOLD) {
            saveAnomalyIfUnresolved(policy, TrustScoreAnomalyType.RAPID_DROP, TrustScoreAnomalySeverity.HIGH, baselineScore, now);
        }
        if (policy.getFalseReportCount() >= FALSE_REPORT_RESTRICTION_THRESHOLD) {
            saveAnomalyIfUnresolved(policy, TrustScoreAnomalyType.FALSE_REPORT_SPIKE, TrustScoreAnomalySeverity.HIGH, baselineScore, now);
        }
        if (isLowAcceptanceRate(policy)) {
            saveAnomalyIfUnresolved(policy, TrustScoreAnomalyType.LOW_ACCEPTANCE_RATE, TrustScoreAnomalySeverity.MEDIUM, baselineScore, now);
        }
    }

    private boolean isLowAcceptanceRate(ReporterModerationPolicy policy) {
        if (policy.getSubmittedCount() < LOW_ACCEPTANCE_MIN_SUBMITTED_COUNT) {
            return false;
        }
        return (double) policy.getAcceptedCount() / policy.getSubmittedCount() <= LOW_ACCEPTANCE_RATE_THRESHOLD;
    }

    private void saveAnomalyIfUnresolved(
            ReporterModerationPolicy policy,
            TrustScoreAnomalyType anomalyType,
            TrustScoreAnomalySeverity severity,
            int baselineScore,
            LocalDateTime now
    ) {
        boolean alreadyDetected = trustScoreAnomalyRepository
                .findByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(policy.getReporterUserId())
                .stream()
                .anyMatch(anomaly -> anomaly.getAnomalyType() == anomalyType);
        if (alreadyDetected) {
            return;
        }

        trustScoreAnomalyRepository.save(TrustScoreAnomaly.builder()
                .reporterUserId(policy.getReporterUserId())
                .reporterUsername(policy.getReporterUsername())
                .anomalyType(anomalyType)
                .severity(severity)
                .baselineScore(baselineScore)
                .observedScore(policy.getTrustScore())
                .submittedCount(policy.getSubmittedCount())
                .acceptedCount(policy.getAcceptedCount())
                .declinedCount(policy.getDeclinedCount())
                .falseReportCount(policy.getFalseReportCount())
                .detectedAt(now)
                .build());
    }

    private void applyHighestPriorityRule(ReporterModerationPolicy policy, LocalDateTime now) {
        trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .filter(rule -> rule.matches(policy))
                .findFirst()
                .filter(rule -> rule.getActionType() == TrustScoreInterventionAction.TEMPORARY_RESTRICT)
                .filter(rule -> rule.getDurationDays() != null)
                .ifPresent(rule -> policy.restrictUntil(now.plusDays(rule.getDurationDays()), rule.getReason()));
    }
}
