package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리뷰에 연결된 검증 완료 사진")
public record PlaceReviewMediaResponse(
        Long reviewMediaId,
        String imageUrl,
        String contentType
) {
    public static PlaceReviewMediaResponse from(PlaceReviewMediaUpload media) {
        return new PlaceReviewMediaResponse(media.getId(), media.getImageUrl(), media.getContentType());
    }
}
