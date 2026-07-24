package com.typenull.pingdom.place.api.dto.place.map;

public record MapClusterItem(
        String clusterId,
        double latitude,
        double longitude,
        long placeCount
) {
}
