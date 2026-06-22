package com.typenull.pingdom.place.api.dto.place;

public record PlaceListItem(
        Long id,
        String name,
        String address,
        String category,
        Double latitude,
        Double longitude,
        Long distanceMeters
) {
}
