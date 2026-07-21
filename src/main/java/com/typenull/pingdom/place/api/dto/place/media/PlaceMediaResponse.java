package com.typenull.pingdom.place.api.dto.place.media;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 미디어 목록 응답")
public record PlaceMediaResponse(
        Long placeId,
        List<PlaceMediaItem> media
) {
}
