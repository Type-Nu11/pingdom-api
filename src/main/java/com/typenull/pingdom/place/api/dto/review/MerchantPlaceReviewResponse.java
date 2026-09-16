package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "상점주 관리 장소 리뷰")
public record MerchantPlaceReviewResponse(
        Long reviewId,
        Long placeId,
        Long userId,
        String recommendReason,
        List<PlaceReviewRecommendReason> recommendReasons,
        String content,
        List<String> imageUrls,
        List<PlaceReviewMediaResponse> reviewMedia,
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
                review.getRecommendReasons() == null ? List.of() : List.copyOf(review.getRecommendReasons()),
                review.getContent(),
                List.copyOf(review.getImageUrls()),
                review.getMediaUploads() == null ? List.of() : review.getMediaUploads().stream().map(PlaceReviewMediaResponse::from).toList(),
                review.getCreatedAt(),
                review.getVisibilityStatus(),
                deletionRequest == null ? null : MerchantPlaceReviewDeletionRequestStatusResponse.from(deletionRequest)
        );
    }
}
