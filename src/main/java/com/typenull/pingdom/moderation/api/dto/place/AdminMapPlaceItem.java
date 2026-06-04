package com.typenull.pingdom.moderation.api.dto.place;

import com.typenull.pingdom.place.domain.PlaceGrowthSnapshot;

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
