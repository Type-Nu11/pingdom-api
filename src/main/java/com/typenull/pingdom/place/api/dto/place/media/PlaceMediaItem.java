package com.typenull.pingdom.place.api.dto.place.media;

import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "장소 미디어 항목")
public record PlaceMediaItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceMediaPurpose purpose,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String s3Key,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String thumbnailUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String thumbnailS3Key,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Long sourceMapImageId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int displayOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
