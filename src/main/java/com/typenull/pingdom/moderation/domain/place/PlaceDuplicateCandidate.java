package com.typenull.pingdom.moderation.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@Entity
@Table(
        name = "place_duplicate_candidate",
        indexes = {
                @Index(name = "idx_place_duplicate_candidate_status_detected", columnList = "status, detected_at DESC, id DESC"),
                @Index(name = "idx_place_duplicate_candidate_left", columnList = "left_place_id, status"),
                @Index(name = "idx_place_duplicate_candidate_right", columnList = "right_place_id, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceDuplicateCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "left_place_id", nullable = false)
    private Long leftPlaceId;

    @Column(name = "right_place_id", nullable = false)
    private Long rightPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_reason", nullable = false, length = 30)
    private PlaceDuplicateMatchReason matchReason;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceDuplicateDecisionStatus status;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "merge_history_id")
    private Long mergeHistoryId;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static PlaceDuplicateCandidate detect(
            Long firstPlaceId,
            Long secondPlaceId,
            PlaceDuplicateMatchReason matchReason,
            BigDecimal confidenceScore,
            Integer distanceMeters,
            LocalDateTime now
    ) {
        Objects.requireNonNull(firstPlaceId, "firstPlaceId must not be null");
        Objects.requireNonNull(secondPlaceId, "secondPlaceId must not be null");
        if (firstPlaceId.equals(secondPlaceId)) {
            throw new IllegalArgumentException("duplicate candidate requires different places");
        }
        validateConfidenceScore(confidenceScore);
        if (distanceMeters != null && distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters must not be negative");
        }

        PlaceDuplicateCandidate candidate = new PlaceDuplicateCandidate();
        candidate.leftPlaceId = Math.min(firstPlaceId, secondPlaceId);
        candidate.rightPlaceId = Math.max(firstPlaceId, secondPlaceId);
        candidate.matchReason = Objects.requireNonNull(matchReason, "matchReason must not be null");
        candidate.confidenceScore = confidenceScore;
        candidate.distanceMeters = distanceMeters;
        candidate.status = PlaceDuplicateDecisionStatus.PENDING;
        candidate.detectedAt = Objects.requireNonNull(now, "now must not be null");
        candidate.updatedAt = now;
        return candidate;
    }

    public void confirm(Long adminUserId, String note, LocalDateTime now) {
        review(PlaceDuplicateDecisionStatus.CONFIRMED, adminUserId, note, now);
    }

    public void reject(Long adminUserId, String note, LocalDateTime now) {
        review(PlaceDuplicateDecisionStatus.REJECTED, adminUserId, note, now);
    }

    public void markMerged(Long mergeHistoryId, LocalDateTime now) {
        requireStatus(PlaceDuplicateDecisionStatus.CONFIRMED);
        this.mergeHistoryId = Objects.requireNonNull(mergeHistoryId, "mergeHistoryId must not be null");
        this.status = PlaceDuplicateDecisionStatus.MERGED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void review(
            PlaceDuplicateDecisionStatus decision,
            Long adminUserId,
            String note,
            LocalDateTime now
    ) {
        requireStatus(PlaceDuplicateDecisionStatus.PENDING);
        if (!StringUtils.hasText(note)) {
            throw new IllegalArgumentException("reviewNote must not be blank");
        }
        reviewedByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        reviewNote = note.trim();
        reviewedAt = Objects.requireNonNull(now, "now must not be null");
        updatedAt = now;
        status = decision;
    }

    private void requireStatus(PlaceDuplicateDecisionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("invalid status transition: " + status + " -> " + expected);
        }
    }

    private static void validateConfidenceScore(BigDecimal confidenceScore) {
        Objects.requireNonNull(confidenceScore, "confidenceScore must not be null");
        if (confidenceScore.compareTo(BigDecimal.ZERO) < 0 || confidenceScore.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidenceScore must be between 0 and 1");
        }
    }
}
