package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상점주 리뷰 삭제 신청 최신 상태")
public record MerchantPlaceReviewDeletionRequestStatusResponse(
        Long deletionRequestId,
        PlaceReviewDeletionRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String reviewNote
) {

    public static MerchantPlaceReviewDeletionRequestStatusResponse from(PlaceReviewDeletionRequest request) {
        return new MerchantPlaceReviewDeletionRequestStatusResponse(
                request.getId(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                request.getReviewNote()
        );
    }
}
