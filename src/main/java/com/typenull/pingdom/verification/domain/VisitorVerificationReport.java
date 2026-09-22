package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광객의 장소 제보 내용과 심사 이력을 관리.
 * 유형에 맞는 구조화 값만 허용하며 정정이 반영되면 이전 심사를 지우고 재심사 대기로 돌아감.
 */
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

    /** 구조화 값이 없는 제보를 생성. 구조화 값이 필수인 유형에는 이 overload를 사용할 수 없음. */
    public static VisitorVerificationReport submit(Long reporterUserId, Long placeId,
            VisitorVerificationReportType reportType, String description, String evidenceUrl, LocalDateTime now) {
        return submit(reporterUserId, placeId, reportType, description, evidenceUrl,
                null, null, null, null, now);
    }

    /** 필수 식별자·본문·시각과 유형별 구조화 값을 검증해 미심사 제보를 생성. */
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
        validateStructuredValue(
                reportType,
                waitTimeMinutes,
                report.languageCode,
                couponUsageStatus,
                crowdLevel
        );
        report.status = VisitorVerificationReportStatus.SUBMITTED;
        report.createdAt = Objects.requireNonNull(now);
        report.updatedAt = now;
        return report;
    }

    /**
     * 대기 시간·언어·쿠폰 사용·혼잡도 유형은 자신에게 해당하는 값 하나만 요구.
     * 그 외 유형은 구조화 값이 없어야 하며 대기 시간은 0~1,440분, 언어는 지정 태그 패턴으로 제한.
     */
    static void validateStructuredValue(
            VisitorVerificationReportType reportType,
            Integer waitTimeMinutes,
            String languageCode,
            CouponUsageStatus couponUsageStatus,
            CrowdLevel crowdLevel
    ) {
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

    /** 승인 또는 거절된 제보만 별도 정정 요청의 대상이 될 수 있음. */
    public boolean canBeCorrected() {
        return status == VisitorVerificationReportStatus.ACCEPTED
                || status == VisitorVerificationReportStatus.REJECTED;
    }

    /**
     * 심사 완료된 제보의 내용을 정정하고 SUBMITTED로 되돌려 재심사를 요구.
     * 유형은 유지하고 기존 심사자·메모·심사 시각은 비움.
     */
    public void applyCorrection(
            String description,
            String evidenceUrl,
            Integer waitTimeMinutes,
            String languageCode,
            CouponUsageStatus couponUsageStatus,
            CrowdLevel crowdLevel,
            LocalDateTime now
    ) {
        if (!canBeCorrected()) {
            throw new IllegalStateException("승인 또는 거절된 제보만 정정할 수 있습니다.");
        }

        String normalizedDescription = requireText(description, "description");
        String normalizedEvidenceUrl = normalize(evidenceUrl);
        String normalizedLanguageCode = normalize(languageCode);
        validateStructuredValue(
                reportType,
                waitTimeMinutes,
                normalizedLanguageCode,
                couponUsageStatus,
                crowdLevel
        );

        this.description = normalizedDescription;
        this.evidenceUrl = normalizedEvidenceUrl;
        this.waitTimeMinutes = waitTimeMinutes;
        this.languageCode = normalizedLanguageCode;
        this.couponUsageStatus = couponUsageStatus;
        this.crowdLevel = crowdLevel;

        status = VisitorVerificationReportStatus.SUBMITTED;
        reviewerAdminUserId = null;
        reviewNote = null;
        reviewedAt = null;
        updatedAt = Objects.requireNonNull(now);
    }

    /** 미심사 제보만 승인·거절하며 거절 사유는 필수. 심사 권한은 호출 서비스에서 확인해야 함. */
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

    /** 공백을 정리한 필수 문자열을 반환하고 비어 있으면 인자 오류로 거부. */
    static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    /** 선택 문자열을 trim하고 null·빈 문자열은 null로 통일. */
    static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
