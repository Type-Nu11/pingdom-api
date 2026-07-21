package com.typenull.pingdom.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "visitor_verification_report_correction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitorVerificationReportCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "report_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_visitor_verification_report_correction_report")
    )
    private VisitorVerificationReport report;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private VisitorVerificationReportType reportType;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

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
    private VisitorVerificationReportCorrectionStatus status;

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

    @Version
    @Column(nullable = false)
    private long version;

    private VisitorVerificationReportCorrection(
            VisitorVerificationReport report,
            Long requesterUserId,
            String description,
            String evidenceUrl,
            Integer waitTimeMinutes,
            String languageCode,
            CouponUsageStatus couponUsageStatus,
            CrowdLevel crowdLevel,
            LocalDateTime submittedAt
    ) {
        this.report = Objects.requireNonNull(report, "report must not be null");
        this.reportType = report.getReportType();
        this.requesterUserId = Objects.requireNonNull(requesterUserId, "requesterUserId must not be null");
        this.description = VisitorVerificationReport.requireText(description, "description");
        this.evidenceUrl = VisitorVerificationReport.normalize(evidenceUrl);
        this.waitTimeMinutes = waitTimeMinutes;
        this.languageCode = VisitorVerificationReport.normalize(languageCode);
        this.couponUsageStatus = couponUsageStatus;
        this.crowdLevel = crowdLevel;
        VisitorVerificationReport.validateStructuredValue(
                report.getReportType(),
                waitTimeMinutes,
                this.languageCode,
                couponUsageStatus,
                crowdLevel
        );
        this.status = VisitorVerificationReportCorrectionStatus.SUBMITTED;
        this.createdAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.updatedAt = submittedAt;
    }

    public static VisitorVerificationReportCorrection submit(
            VisitorVerificationReport report,
            Long requesterUserId,
            String description,
            String evidenceUrl,
            Integer waitTimeMinutes,
            String languageCode,
            CouponUsageStatus couponUsageStatus,
            CrowdLevel crowdLevel,
            LocalDateTime submittedAt
    ) {
        if (!report.canBeCorrected()) {
            throw new IllegalStateException("승인 또는 거절된 제보만 정정할 수 있습니다.");
        }
        return new VisitorVerificationReportCorrection(
                report,
                requesterUserId,
                description,
                evidenceUrl,
                waitTimeMinutes,
                languageCode,
                couponUsageStatus,
                crowdLevel,
                submittedAt
        );
    }

    public void review(
            Long adminUserId,
            VisitorVerificationReportCorrectionStatus decision,
            String reviewNote,
            LocalDateTime now
    ) {
        if (status != VisitorVerificationReportCorrectionStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 정정만 심사할 수 있습니다.");
        }
        if (decision == null || decision == VisitorVerificationReportCorrectionStatus.SUBMITTED) {
            throw new IllegalArgumentException("정정 심사 결과는 승인 또는 거절이어야 합니다.");
        }
        String normalizedNote = VisitorVerificationReport.normalize(reviewNote);
        if (decision == VisitorVerificationReportCorrectionStatus.REJECTED && normalizedNote == null) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        status = decision;
        reviewerAdminUserId = Objects.requireNonNull(adminUserId);
        this.reviewNote = normalizedNote;
        reviewedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }
}
