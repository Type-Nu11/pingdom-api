package com.typenull.pingdom.place.api.dto.place.map;

import io.swagger.v3.oas.annotations.media.Schema;

public record MapMarkerItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(minimum = "-90.0", maximum = "90.0", requiredMode = Schema.RequiredMode.REQUIRED)
        double latitude,
        @Schema(minimum = "-180.0", maximum = "180.0", requiredMode = Schema.RequiredMode.REQUIRED)
        double longitude,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long photoCount
) {
}
