package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import java.time.LocalDateTime;

public record MerchantPlaceReviewDeletionRequestResponse(
        Long deletionRequestId,
        Long reviewId,
        Long placeId,
        PlaceReviewVisibilityStatus reviewVisibilityStatus,
        PlaceReviewDeletionRequestStatus status,
        LocalDateTime requestedAt
) {
    public static MerchantPlaceReviewDeletionRequestResponse from(PlaceReviewDeletionRequest request) {
        return new MerchantPlaceReviewDeletionRequestResponse(
                request.getId(),
                request.getReview().getId(),
                request.getReview().getPlace().getId(),
                request.getReview().getVisibilityStatus(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
