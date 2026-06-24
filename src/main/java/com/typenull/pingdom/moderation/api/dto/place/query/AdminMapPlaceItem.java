package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;

public record AdminMapPlaceItem(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        PlaceGrowthSnapshot placeGrowth
) {
}
