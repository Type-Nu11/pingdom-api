package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record AdminMapPlaceItem(
        Long id,
        String name,
        String address,
        String category,
        String categoryName,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        PlaceGrowthSnapshot placeGrowth
) {
}
