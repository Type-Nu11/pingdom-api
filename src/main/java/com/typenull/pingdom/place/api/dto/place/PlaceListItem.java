package com.typenull.pingdom.place.api.dto.place;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record PlaceListItem(
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
        String address,
        String category,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        Long distanceMeters
) {
}
