package com.typenull.pingdom.place.api.dto.place.map;

public record MapMarkerItem(
        long placeId,
        String name,
        String category,
        String imageUrl,
        double latitude,
        double longitude,
        long photoCount
) {
}
