package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminPlaceReviewDeletionRequestResponse(
        Long deletionRequestId,
        PlaceReviewDeletionRequestStatus status,
        String requestReason,
        Long requesterUserId,
        LocalDateTime requestedAt,
        Long reviewerAdminUserId,
        String reviewNote,
        LocalDateTime reviewedAt,
        Long reviewId,
        Long placeId,
        Long reviewAuthorUserId,
        String recommendReason,
        String content,
        List<String> imageUrls,
        PlaceReviewVisibilityStatus reviewVisibilityStatus,
        LocalDateTime reviewCreatedAt
) {
    public static AdminPlaceReviewDeletionRequestResponse from(PlaceReviewDeletionRequest request) {
        var review = request.getReview();
        return new AdminPlaceReviewDeletionRequestResponse(
                request.getId(),
                request.getStatus(),
                request.getRequestReason(),
                request.getRequesterUserId(),
                request.getCreatedAt(),
                request.getReviewerAdminUserId(),
                request.getReviewNote(),
                request.getReviewedAt(),
                review.getId(),
                review.getPlace().getId(),
                review.getUserId(),
                review.getRecommendReason(),
                review.getContent(),
                List.copyOf(review.getImageUrls()),
                review.getVisibilityStatus(),
                review.getCreatedAt()
        );
    }
}
