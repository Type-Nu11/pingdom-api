package com.typenull.pingdom.moderation.api.dto.place.quality;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record AdminMapPlaceTouristInfoUpdateResponse(
        Long placeId,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        String message
) {
}
