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

/**
 * 신고 접수·승인·반려 통계로 신고자 신뢰도와 제한, 이미지 자동 숨김을 관리합니다.
 * 기존 신고자 정책 갱신은 행 잠금을 사용하지만 최초 정책 생성 경합을 별도로 재시도하지는 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
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

    // 신고자의 현재 제한 상태를 정리한 뒤 새로운 신고를 등록할 수 있는지 검증합니다.
    public void validateCanReport(Long reporterUserId, LocalDateTime now) {
        reporterPolicyRepository.findById(reporterUserId)
                .ifPresent(policy -> {
                    policy.clearExpiredRestriction(now);
                    if (policy.isRestricted(now)) {
                        throw new MapException(MapErrorCode.REPORTER_RESTRICTED);
                    }
                });
    }

    // 신고 접수 건수를 기록하고 신고자 정책을 저장합니다.
    public void recordSubmitted(Long reporterUserId, String reporterUsername) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        policy.recordSubmitted(reporterUsername);
        reporterPolicyRepository.save(policy);
    }

    // 승인된 신고를 반영하고 신뢰도 이상 및 개입 규칙을 평가합니다.
    public void recordAccepted(Long reporterUserId, String reporterUsername) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        int baselineScore = policy.getTrustScore();
        LocalDateTime now = LocalDateTime.now(clock);
        policy.recordAccepted(reporterUsername);
        detectAnomalies(policy, baselineScore, now);
        applyHighestPriorityRule(policy, now);
        reporterPolicyRepository.save(policy);
    }

    /**
     * 신고 반려를 신뢰도에 반영하고 허위 신고가 세 번 이상이면 전달 시각부터 7일간 제한한다.
     * 이어 이상 징후와 최우선 개입 규칙을 적용해 제한 종료일이 다시 정해질 수 있으며 정책을 저장한다.
     */
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

    /**
     * 공개 이미지의 PENDING·ACCEPTED 신고를 신고자 신뢰도 가중치로 합산해 3 이상이면 숨김 전이를 시도한다.
     * 실제로 숨겨진 경우만 연결 장소의 사진 수를 줄이고 true를 반환한다. 누락·이미 숨김·기준 미달은 false다.
     */
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

    // 이미지가 실제로 숨겨진 경우 장소의 사진 집계를 함께 감소시킵니다.
    private void decreasePhotoCountIfNeeded(MapImage mapImage, boolean hidden) {
        if (!hidden || mapImage.getMapPlace() == null) {
            return;
        }
        placeGrowthService.decreasePhotoCount(mapImage.getMapPlace().getId());
    }

    // 활성 신고에 포함된 신고자 정책을 한 번에 조회해 신고자 ID로 색인합니다.
    private Map<Long, ReporterModerationPolicy> loadPoliciesByReporterId(List<PostReport> activeReports) {
        List<Long> reporterIds = activeReports.stream()
                .map(PostReport::getReporterUserId)
                .distinct()
                .toList();
        return reporterPolicyRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(ReporterModerationPolicy::getReporterUserId, Function.identity()));
    }

    // 신고자의 신뢰도 구간을 자동 숨김 판단용 가중치로 변환합니다.
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

    // 비관적 잠금으로 기존 정책을 가져오거나 신규 신고자 정책을 생성합니다.
    private ReporterModerationPolicy getOrCreate(Long reporterUserId, String reporterUsername) {
        return reporterPolicyRepository.findByReporterUserIdForUpdate(reporterUserId)
                .orElseGet(() -> ReporterModerationPolicy.create(reporterUserId, reporterUsername));
    }

    // 신뢰도 급락, 허위 신고 증가, 낮은 승인율을 미해결 이상으로 기록합니다.
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

    // 최소 신고 건수와 승인율 기준을 모두 만족하는지 확인합니다.
    private boolean isLowAcceptanceRate(ReporterModerationPolicy policy) {
        if (policy.getSubmittedCount() < LOW_ACCEPTANCE_MIN_SUBMITTED_COUNT) {
            return false;
        }
        return (double) policy.getAcceptedCount() / policy.getSubmittedCount() <= LOW_ACCEPTANCE_RATE_THRESHOLD;
    }

    // 동일 유형의 미해결 이상이 없을 때 현재 신뢰도 지표를 저장합니다.
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

    /**
     * 우선순위·ID 순으로 처음 일치한 규칙 하나만 선택합니다.
     * 그 규칙이 기간을 가진 TEMPORARY_RESTRICT일 때만 제한하므로, 앞선 다른 액션을 건너뛰고 후순위 제한을 찾지는 않습니다.
     */
    private void applyHighestPriorityRule(ReporterModerationPolicy policy, LocalDateTime now) {
        trustScoreInterventionRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .filter(rule -> rule.matches(policy))
                .findFirst()
                .filter(rule -> rule.getActionType() == TrustScoreInterventionAction.TEMPORARY_RESTRICT)
                .filter(rule -> rule.getDurationDays() != null)
                .ifPresent(rule -> policy.restrictUntil(now.plusDays(rule.getDurationDays()), rule.getReason()));
    }
}
