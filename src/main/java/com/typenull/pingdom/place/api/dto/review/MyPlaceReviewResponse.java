package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내가 작성한 장소 리뷰")
public record MyPlaceReviewResponse(
        Long reviewId,
        Long placeId,
        String recommendReason,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt,
        PlaceReviewVisibilityStatus visibilityStatus
) {

    public static MyPlaceReviewResponse from(PlaceReview review) {
        return new MyPlaceReviewResponse(
                review.getId(),
                review.getPlace().getId(),
                review.getRecommendReason(),
                review.getContent(),
                review.getImageUrls(),
                review.getCreatedAt(),
                review.getVisibilityStatus()
        );
    }
}
