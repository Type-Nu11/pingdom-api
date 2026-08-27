package com.typenull.pingdom.place.api.dto.localhot;

public record PlaceLocalHotQuery(
        Double latitude,
        Double longitude,
        String regionCode,
        int page,
        int limit
) {
}
