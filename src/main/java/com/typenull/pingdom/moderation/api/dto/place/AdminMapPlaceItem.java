package com.typenull.pingdom.moderation.api.dto.place;

public record AdminMapPlaceItem(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long userId,
        String Registrant
) {
}
