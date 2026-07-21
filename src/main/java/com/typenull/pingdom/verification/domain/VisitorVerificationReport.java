package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "visitor_verification_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitorVerificationReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private VisitorVerificationReportType reportType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Column(name = "wait_time_minutes")
    private Integer waitTimeMinutes;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_usage_status", length = 20)
    private CouponUsageStatus couponUsageStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "crowd_level", length = 20)
    private CrowdLevel crowdLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitorVerificationReportStatus status;

    @Column(name = "reviewer_admin_user_id")
    private Long reviewerAdminUserId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    private long version;

    public static VisitorVerificationReport submit(Long reporterUserId, Long placeId,
            VisitorVerificationReportType reportType, String description, String evidenceUrl, LocalDateTime now) {
        return submit(reporterUserId, placeId, reportType, description, evidenceUrl,
                null, null, null, null, now);
    }

    public static VisitorVerificationReport submit(Long reporterUserId, Long placeId,
            VisitorVerificationReportType reportType, String description, String evidenceUrl,
            Integer waitTimeMinutes, String languageCode, CouponUsageStatus couponUsageStatus,
            CrowdLevel crowdLevel, LocalDateTime now) {
        VisitorVerificationReport report = new VisitorVerificationReport();
        report.reporterUserId = Objects.requireNonNull(reporterUserId);
        report.placeId = Objects.requireNonNull(placeId);
        report.reportType = Objects.requireNonNull(reportType);
        report.description = requireText(description, "description");
        report.evidenceUrl = normalize(evidenceUrl);
        report.waitTimeMinutes = waitTimeMinutes;
        report.languageCode = normalize(languageCode);
        report.couponUsageStatus = couponUsageStatus;
        report.crowdLevel = crowdLevel;
        report.validateStructuredValue();
        report.status = VisitorVerificationReportStatus.SUBMITTED;
        report.createdAt = Objects.requireNonNull(now);
        report.updatedAt = now;
        return report;
    }

    private void validateStructuredValue() {
        int providedValueCount = (waitTimeMinutes == null ? 0 : 1)
                + (languageCode == null ? 0 : 1)
                + (couponUsageStatus == null ? 0 : 1)
                + (crowdLevel == null ? 0 : 1);

        switch (reportType) {
            case WAIT_TIME -> {
                if (providedValueCount != 1 || waitTimeMinutes == null
                        || waitTimeMinutes < 0 || waitTimeMinutes > 1440) {
                    throw new IllegalArgumentException("waitTimeMinutes must be between 0 and 1440");
                }
            }
            case LANGUAGE_SUPPORT -> {
                if (providedValueCount != 1 || languageCode == null
                        || !languageCode.matches("^[a-z]{2,3}(-[A-Z]{2})?$")) {
                    throw new IllegalArgumentException("languageCode must be a supported language tag");
                }
            }
            case COUPON_USAGE -> {
                if (providedValueCount != 1 || couponUsageStatus == null) {
                    throw new IllegalArgumentException("couponUsageStatus must be provided alone");
                }
            }
            case CROWD_LEVEL -> {
                if (providedValueCount != 1 || crowdLevel == null) {
                    throw new IllegalArgumentException("crowdLevel must be provided alone");
                }
            }
            default -> {
                if (providedValueCount != 0) {
                    throw new IllegalArgumentException("structured value is not allowed for this report type");
                }
            }
        }
    }

    public void review(Long adminUserId, VisitorVerificationReportStatus decision, String reviewNote,
            LocalDateTime now) {
        if (status != VisitorVerificationReportStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 제보만 심사할 수 있습니다.");
        }
        if (decision == null || decision == VisitorVerificationReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("심사 결과는 승인 또는 거절이어야 합니다.");
        }
        String normalizedNote = normalize(reviewNote);
        if (decision == VisitorVerificationReportStatus.REJECTED && normalizedNote == null) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        status = decision;
        reviewerAdminUserId = Objects.requireNonNull(adminUserId);
        this.reviewNote = normalizedNote;
        reviewedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
