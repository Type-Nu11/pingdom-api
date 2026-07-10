package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaceAutocompleteItem(
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
        String address,
        String category,
        Double latitude,
        Double longitude,
        Double distanceMeters
) {
}
