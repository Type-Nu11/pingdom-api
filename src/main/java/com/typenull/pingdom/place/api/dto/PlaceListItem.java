package com.typenull.pingdom.place.api.dto;

public record PlaceListItem(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude
) {
}
