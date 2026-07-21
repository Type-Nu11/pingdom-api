package com.typenull.pingdom.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "scout_field_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoutFieldReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scout_user_id")
    private Long scoutUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ScoutFieldReportType reportType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoutFieldReportStatus status;

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

    public static ScoutFieldReport submit(
            Long scoutUserId,
            Long placeId,
            ScoutFieldReportType reportType,
            String description,
            String evidenceUrl,
            LocalDateTime now
    ) {
        ScoutFieldReport report = new ScoutFieldReport();
        report.scoutUserId = Objects.requireNonNull(scoutUserId);
        report.placeId = Objects.requireNonNull(placeId);
        report.reportType = Objects.requireNonNull(reportType);
        report.description = requireText(description, "description");
        report.evidenceUrl = normalize(evidenceUrl);
        report.status = ScoutFieldReportStatus.SUBMITTED;
        report.createdAt = Objects.requireNonNull(now);
        report.updatedAt = now;
        return report;
    }

    public void review(
            Long adminUserId,
            ScoutFieldReportStatus decision,
            String reviewNote,
            LocalDateTime now
    ) {
        if (status != ScoutFieldReportStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 Scout 현장 제보만 심사할 수 있습니다.");
        }
        if (decision == null || decision == ScoutFieldReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("심사 결과는 승인 또는 거절이어야 합니다.");
        }

        String normalizedNote = normalize(reviewNote);
        if (decision == ScoutFieldReportStatus.REJECTED && normalizedNote == null) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }

        status = decision;
        reviewerAdminUserId = Objects.requireNonNull(adminUserId);
        this.reviewNote = normalizedNote;
        reviewedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
