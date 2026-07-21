package com.typenull.pingdom.place.domain.place.information.report;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_information_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceInformationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_information_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_information_report_place")
    )
    private MapPlace place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "place_information_evidence_id",
            foreignKey = @ForeignKey(name = "fk_place_information_report_evidence")
    )
    private PlaceInformationEvidence evidence;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private PlaceInformationReportTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private PlaceInformationReportReasonType reasonType;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlaceInformationReportStatus status;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter(AccessLevel.NONE)
    private List<PlaceInformationReportDispute> disputes = new ArrayList<>();

    private PlaceInformationReport(
            MapPlace place,
            PlaceInformationEvidence evidence,
            Long reporterUserId,
            PlaceInformationReportTargetType targetType,
            PlaceInformationReportReasonType reasonType,
            String description,
            String evidenceUrl,
            LocalDateTime submittedAt
    ) {
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.evidence = evidence;
        if (evidence != null && !Objects.equals(evidence.getPlace().getId(), place.getId())) {
            throw new IllegalArgumentException("evidence must belong to the reported place");
        }
        this.reporterUserId = Objects.requireNonNull(reporterUserId, "reporterUserId must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.reasonType = Objects.requireNonNull(reasonType, "reasonType must not be null");
        this.description = requireText(description, "description must not be blank");
        this.evidenceUrl = trimToNull(evidenceUrl);
        this.status = PlaceInformationReportStatus.SUBMITTED;
        this.createdAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.updatedAt = submittedAt;
    }

    public static PlaceInformationReport submit(
            MapPlace place,
            PlaceInformationEvidence evidence,
            Long reporterUserId,
            PlaceInformationReportTargetType targetType,
            PlaceInformationReportReasonType reasonType,
            String description,
            String evidenceUrl,
            LocalDateTime submittedAt
    ) {
        return new PlaceInformationReport(
                place,
                evidence,
                reporterUserId,
                targetType,
                reasonType,
                description,
                evidenceUrl,
                submittedAt
        );
    }

    public boolean canBeReviewed() {
        return status == PlaceInformationReportStatus.SUBMITTED
                || status == PlaceInformationReportStatus.UNDER_REVIEW
                || status == PlaceInformationReportStatus.DISPUTED;
    }

    public void startReview(Long adminUserId, LocalDateTime reviewedAt) {
        ensureNotTerminal();
        this.status = PlaceInformationReportStatus.UNDER_REVIEW;
        this.reviewedByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        this.updatedAt = reviewedAt;
    }

    public void accept(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        review(PlaceInformationReportStatus.ACCEPTED, adminUserId, reviewReason, reviewedAt, true);
    }

    public void reject(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        review(PlaceInformationReportStatus.REJECTED, adminUserId, reviewReason, reviewedAt, true);
    }

    public void resolve(Long adminUserId, String reviewReason, LocalDateTime resolvedAt) {
        if (status != PlaceInformationReportStatus.DISPUTED && status != PlaceInformationReportStatus.UNDER_REVIEW) {
            throw new IllegalStateException("only disputed or under-review reports can be resolved");
        }
        review(PlaceInformationReportStatus.RESOLVED, adminUserId, reviewReason, resolvedAt, true);
        this.resolvedAt = resolvedAt;
    }

    public void cancel(Long reporterUserId, LocalDateTime canceledAt) {
        if (!Objects.equals(this.reporterUserId, reporterUserId)) {
            throw new IllegalArgumentException("only reporter can cancel the report");
        }
        if (status != PlaceInformationReportStatus.SUBMITTED) {
            throw new IllegalStateException("only submitted reports can be canceled");
        }
        this.status = PlaceInformationReportStatus.CANCELED;
        this.resolvedAt = Objects.requireNonNull(canceledAt, "canceledAt must not be null");
        this.updatedAt = canceledAt;
    }

    public PlaceInformationReportDispute submitDispute(
            Long disputedByUserId,
            String description,
            String evidenceUrl,
            LocalDateTime submittedAt
    ) {
        if (status != PlaceInformationReportStatus.ACCEPTED && status != PlaceInformationReportStatus.UNDER_REVIEW) {
            throw new IllegalStateException("only accepted or under-review reports can be disputed");
        }
        PlaceInformationReportDispute dispute = PlaceInformationReportDispute.submit(
                this,
                disputedByUserId,
                description,
                evidenceUrl,
                submittedAt
        );
        this.disputes.add(dispute);
        this.status = PlaceInformationReportStatus.DISPUTED;
        this.updatedAt = submittedAt;
        return dispute;
    }

    public List<PlaceInformationReportDispute> currentDisputes() {
        return Collections.unmodifiableList(disputes);
    }

    private void review(
            PlaceInformationReportStatus nextStatus,
            Long adminUserId,
            String reviewReason,
            LocalDateTime reviewedAt,
            boolean requireReason
    ) {
        if (!canBeReviewed()) {
            throw new IllegalStateException("report cannot be reviewed");
        }
        String normalizedReason = trimToNull(reviewReason);
        if (requireReason && normalizedReason == null) {
            throw new IllegalArgumentException("reviewReason must not be blank");
        }
        LocalDateTime reviewedTime = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        this.status = Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        this.reviewedByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        this.reviewReason = normalizedReason;
        this.reviewedAt = reviewedTime;
        this.updatedAt = reviewedTime;
    }

    private void ensureNotTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("terminal report cannot be changed");
        }
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
