package com.typenull.pingdom.place.domain.place.information;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_information_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceInformationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_information_evidence_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_information_evidence_place")
    )
    private MapPlace place;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private PlaceInformationSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private PlaceInformationEvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private PlaceInformationVerificationStatus verificationStatus;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PlaceInformationEvidence(
            MapPlace place,
            PlaceInformationSourceType sourceType,
            PlaceInformationEvidenceType evidenceType,
            String externalReference,
            String referenceUrl,
            String description,
            Long submittedByUserId,
            LocalDateTime submittedAt
    ) {
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        this.evidenceType = Objects.requireNonNull(evidenceType, "evidenceType must not be null");
        this.externalReference = trimToNull(externalReference);
        this.referenceUrl = trimToNull(referenceUrl);
        this.description = trimToNull(description);
        if (!hasEvidencePayload()) {
            throw new IllegalArgumentException("evidence payload must not be empty");
        }
        this.submittedByUserId = submittedByUserId;
        this.verificationStatus = PlaceInformationVerificationStatus.UNVERIFIED;
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.createdAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    public static PlaceInformationEvidence submit(
            MapPlace place,
            PlaceInformationSourceType sourceType,
            PlaceInformationEvidenceType evidenceType,
            String externalReference,
            String referenceUrl,
            String description,
            Long submittedByUserId,
            LocalDateTime submittedAt
    ) {
        return new PlaceInformationEvidence(
                place,
                sourceType,
                evidenceType,
                externalReference,
                referenceUrl,
                description,
                submittedByUserId,
                submittedAt
        );
    }

    public void markOwnerSubmitted(LocalDateTime updatedAt) {
        this.verificationStatus = PlaceInformationVerificationStatus.OWNER_SUBMITTED;
        this.reviewedByAdminUserId = null;
        this.reviewedAt = null;
        this.reviewReason = null;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void verifyByAdmin(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId must not be null");
        }
        LocalDateTime reviewedTime = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        this.verificationStatus = PlaceInformationVerificationStatus.ADMIN_VERIFIED;
        this.reviewedByAdminUserId = adminUserId;
        this.reviewReason = trimToNull(reviewReason);
        this.reviewedAt = reviewedTime;
        this.updatedAt = reviewedTime;
    }

    public void reject(Long adminUserId, String reviewReason, LocalDateTime reviewedAt) {
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId must not be null");
        }
        String reason = trimToNull(reviewReason);
        if (reason == null) {
            throw new IllegalArgumentException("reviewReason must not be blank");
        }
        LocalDateTime reviewedTime = Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        this.verificationStatus = PlaceInformationVerificationStatus.REJECTED;
        this.reviewedByAdminUserId = adminUserId;
        this.reviewReason = reason;
        this.reviewedAt = reviewedTime;
        this.updatedAt = reviewedTime;
    }

    public void dispute(String reviewReason, LocalDateTime updatedAt) {
        this.verificationStatus = PlaceInformationVerificationStatus.DISPUTED;
        this.reviewReason = trimToNull(reviewReason);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private boolean hasEvidencePayload() {
        return externalReference != null || referenceUrl != null || description != null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
