package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import com.typenull.pingdom.place.domain.place.GeocodingSource;

public record PlaceAutocompleteItem(
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
        String address,
        @Schema(nullable = true)
        String roadAddress,
        @Schema(nullable = true)
        String jibunAddress,
        @Schema(nullable = true)
        String postalCode,
        GeocodingSource geocodingSource,
        String category,
        Double latitude,
        Double longitude,
        Double distanceMeters
) {
}
