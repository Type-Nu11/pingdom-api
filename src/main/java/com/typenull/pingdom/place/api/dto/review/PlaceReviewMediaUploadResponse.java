package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "리뷰 작성 전 임시 저장된 사진 업로드 결과")
public record PlaceReviewMediaUploadResponse(
        Long reviewMediaId,
        String imageUrl,
        String contentType,
        long fileSize,
        @Schema(description = "리뷰 연결 전까지 유효한 시각", format = "date-time") LocalDateTime expiresAt
) {
    public static PlaceReviewMediaUploadResponse from(PlaceReviewMediaUpload media) {
        return new PlaceReviewMediaUploadResponse(
                media.getId(), media.getImageUrl(), media.getContentType(), media.getFileSize(), media.getExpiresAt()
        );
    }
}
