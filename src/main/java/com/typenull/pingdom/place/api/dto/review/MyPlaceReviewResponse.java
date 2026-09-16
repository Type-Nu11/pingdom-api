package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내가 작성한 장소 리뷰")
public record MyPlaceReviewResponse(
        Long reviewId,
        Long placeId,
        String recommendReason,
        List<PlaceReviewRecommendReason> recommendReasons,
        String content,
        List<String> imageUrls,
        List<PlaceReviewMediaResponse> reviewMedia,
        LocalDateTime createdAt,
        PlaceReviewVisibilityStatus visibilityStatus
) {

    public static MyPlaceReviewResponse from(PlaceReview review) {
        return new MyPlaceReviewResponse(
                review.getId(),
                review.getPlace().getId(),
                review.getRecommendReason(),
                review.getRecommendReasons() == null ? List.of() : List.copyOf(review.getRecommendReasons()),
                review.getContent(),
                List.copyOf(review.getImageUrls()),
                review.getMediaUploads() == null ? List.of() : review.getMediaUploads().stream().map(PlaceReviewMediaResponse::from).toList(),
                review.getCreatedAt(),
                review.getVisibilityStatus()
        );
    }
}
