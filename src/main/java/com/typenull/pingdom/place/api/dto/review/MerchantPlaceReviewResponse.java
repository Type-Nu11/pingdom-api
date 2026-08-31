package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "상점주 관리 장소 리뷰")
public record MerchantPlaceReviewResponse(
        Long reviewId,
        Long placeId,
        Long userId,
        String recommendReason,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt,
        PlaceReviewVisibilityStatus visibilityStatus,
        @Schema(description = "최신 삭제 신청 정보. 신청이 없으면 null", nullable = true)
        MerchantPlaceReviewDeletionRequestStatusResponse deletionRequest
) {

    public static MerchantPlaceReviewResponse from(
            PlaceReview review,
            PlaceReviewDeletionRequest deletionRequest
    ) {
        return new MerchantPlaceReviewResponse(
                review.getId(),
                review.getPlace().getId(),
                review.getUserId(),
                review.getRecommendReason(),
                review.getContent(),
                review.getImageUrls(),
                review.getCreatedAt(),
                review.getVisibilityStatus(),
                deletionRequest == null ? null : MerchantPlaceReviewDeletionRequestStatusResponse.from(deletionRequest)
        );
    }
}
