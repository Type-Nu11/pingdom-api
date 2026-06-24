package com.typenull.pingdom.domain.admin.dto.place;

public record AdminMapPlaceItem(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long userId
) {
}
