package com.typenull.pingdom.place.api.dto.place.media;

import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "장소 미디어 항목")
public record PlaceMediaItem(
        Long id,
        Long placeId,
        PlaceMediaPurpose purpose,
        String imageUrl,
        @Schema(nullable = true)
        String s3Key,
        @Schema(nullable = true)
        String thumbnailUrl,
        @Schema(nullable = true)
        String thumbnailS3Key,
        @Schema(nullable = true)
        Long sourceMapImageId,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PlaceMediaItem from(PlaceMedia media) {
        return new PlaceMediaItem(
                media.getId(),
                media.getPlace().getId(),
                media.getPurpose(),
                media.getImageUrl(),
                media.getS3Key(),
                media.getThumbnailUrl(),
                media.getThumbnailS3Key(),
                media.getSourceMapImageId(),
                media.getDisplayOrder(),
                media.getCreatedAt(),
                media.getUpdatedAt()
        );
    }
}
