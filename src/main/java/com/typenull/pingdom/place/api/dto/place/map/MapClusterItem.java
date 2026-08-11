package com.typenull.pingdom.place.api.dto.place.map;

import io.swagger.v3.oas.annotations.media.Schema;

public record MapClusterItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String clusterId,
        @Schema(minimum = "-90.0", maximum = "90.0", requiredMode = Schema.RequiredMode.REQUIRED)
        double latitude,
        @Schema(minimum = "-180.0", maximum = "180.0", requiredMode = Schema.RequiredMode.REQUIRED)
        double longitude,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long placeCount
) {
}
