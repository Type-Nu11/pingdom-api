package com.typenull.pingdom.place.api.dto.place;

public record PlaceAutocompleteItem(
        Long id,
        String name,
        String address,
        String category,
        Double latitude,
        Double longitude,
        Double distanceMeters
) {
}
