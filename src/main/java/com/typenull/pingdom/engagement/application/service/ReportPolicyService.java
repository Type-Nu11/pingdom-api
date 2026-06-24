package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportPolicyService {

    private static final int AUTO_HIDE_WEIGHT_THRESHOLD = 3;
    private static final int FALSE_REPORT_RESTRICTION_THRESHOLD = 3;
    private static final int RESTRICTION_DAYS = 7;
    private static final String AUTO_HIDE_REASON = "REPORT_POLICY_AUTO_HIDE";
    private static final String RESTRICTION_REASON = "FALSE_REPORT_THRESHOLD_EXCEEDED";

    private final ReporterModerationPolicyRepository reporterPolicyRepository;
    private final PostReportRepository postReportRepository;

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
        policy.recordAccepted(reporterUsername);
        reporterPolicyRepository.save(policy);
    }

    public void recordDeclined(Long reporterUserId, String reporterUsername, LocalDateTime now) {
        ReporterModerationPolicy policy = getOrCreate(reporterUserId, reporterUsername);
        policy.recordDeclined(reporterUsername);
        if (policy.getFalseReportCount() >= FALSE_REPORT_RESTRICTION_THRESHOLD) {
            policy.restrictUntil(now.plusDays(RESTRICTION_DAYS), RESTRICTION_REASON);
        }
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
        double weightedScore = activeReports.stream()
                .mapToDouble(report -> reporterWeight(report.getReporterUserId()))
                .sum();

        if (weightedScore < AUTO_HIDE_WEIGHT_THRESHOLD) {
            return false;
        }

        mapImage.autoHide(AUTO_HIDE_REASON, now, null);
        return true;
    }

    private double reporterWeight(Long reporterUserId) {
        return reporterPolicyRepository.findById(reporterUserId)
                .map(policy -> {
                    if (policy.getTrustScore() >= 80) {
                        return 1.0;
                    }
                    if (policy.getTrustScore() >= 50) {
                        return 0.75;
                    }
                    return 0.5;
                })
                .orElse(1.0);
    }

    private ReporterModerationPolicy getOrCreate(Long reporterUserId, String reporterUsername) {
        return reporterPolicyRepository.findById(reporterUserId)
                .orElseGet(() -> ReporterModerationPolicy.create(reporterUserId, reporterUsername));
    }
}
