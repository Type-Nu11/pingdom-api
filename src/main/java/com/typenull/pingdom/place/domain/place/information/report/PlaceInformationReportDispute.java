package com.typenull.pingdom.place.domain.place.information.report;

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
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_information_report_dispute")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceInformationReportDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_information_report_dispute_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "place_information_report_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_information_report_dispute_report")
    )
    private PlaceInformationReport report;

    @Column(name = "disputed_by_user_id", nullable = false)
    private Long disputedByUserId;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlaceInformationDisputeStatus status;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private PlaceInformationReportDispute(
            PlaceInformationReport report,
            Long disputedByUserId,
            String description,
            String evidenceUrl,
            LocalDateTime submittedAt
    ) {
        this.report = Objects.requireNonNull(report, "report must not be null");
        this.disputedByUserId = Objects.requireNonNull(disputedByUserId, "disputedByUserId must not be null");
        this.description = requireText(description, "description must not be blank");
        this.evidenceUrl = trimToNull(evidenceUrl);
        this.status = PlaceInformationDisputeStatus.SUBMITTED;
        this.createdAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.updatedAt = submittedAt;
    }

    static PlaceInformationReportDispute submit(
            PlaceInformationReport report,
            Long disputedByUserId,
            String description,
            String evidenceUrl,
            LocalDateTime submittedAt
    ) {
        return new PlaceInformationReportDispute(report, disputedByUserId, description, evidenceUrl, submittedAt);
    }

    public void accept(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        review(PlaceInformationDisputeStatus.ACCEPTED, adminUserId, reviewReason, reviewedAt);
    }

    public void reject(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        review(PlaceInformationDisputeStatus.REJECTED, adminUserId, reviewReason, reviewedAt);
    }

    private void review(
            PlaceInformationDisputeStatus nextStatus,
            Long adminUserId,
            String reviewReason,
            LocalDateTime reviewedAt
    ) {
        if (status.isProcessed()) {
            throw new IllegalStateException("processed dispute cannot be changed");
        }
        String normalizedReason = trimToNull(reviewReason);
        if (normalizedReason == null) {
            throw new IllegalArgumentException("reviewReason must not be blank");
        }
        LocalDateTime reviewedTime = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        this.status = Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        this.reviewedByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        this.reviewReason = normalizedReason;
        this.reviewedAt = reviewedTime;
        this.updatedAt = reviewedTime;
    }

    private static String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
