package com.typenull.pingdom.place.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "place_review_deletion_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReviewDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private PlaceReview review;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceReviewDeletionRequestStatus status;

    @Column(name = "request_reason", nullable = false, length = 500)
    private String requestReason;

    @Column(name = "reviewer_admin_user_id")
    private Long reviewerAdminUserId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private PlaceReviewDeletionRequest(
            PlaceReview review,
            Long requesterUserId,
            String requestReason,
            LocalDateTime now
    ) {
        this.review = Objects.requireNonNull(review);
        this.requesterUserId = Objects.requireNonNull(requesterUserId);
        this.requestReason = requireText(requestReason, "requestReason");
        this.status = PlaceReviewDeletionRequestStatus.PENDING;
        this.createdAt = Objects.requireNonNull(now);
    }

    public static PlaceReviewDeletionRequest submit(
            PlaceReview review,
            Long requesterUserId,
            String requestReason,
            LocalDateTime now
    ) {
        return new PlaceReviewDeletionRequest(review, requesterUserId, requestReason, now);
    }

    public void review(
            Long adminUserId,
            PlaceReviewDeletionRequestStatus decision,
            String reviewNote,
            LocalDateTime now
    ) {
        if (status != PlaceReviewDeletionRequestStatus.PENDING) {
            throw new IllegalStateException("이미 심사 완료된 삭제 신청입니다.");
        }
        if (decision != PlaceReviewDeletionRequestStatus.APPROVED
                && decision != PlaceReviewDeletionRequestStatus.REJECTED) {
            throw new IllegalArgumentException("삭제 신청 심사 결과가 올바르지 않습니다.");
        }
        if (decision == PlaceReviewDeletionRequestStatus.REJECTED) {
            reviewNote = requireText(reviewNote, "reviewNote");
        } else {
            reviewNote = normalize(reviewNote);
        }
        status = decision;
        reviewerAdminUserId = Objects.requireNonNull(adminUserId);
        this.reviewNote = reviewNote;
        reviewedAt = Objects.requireNonNull(now);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
