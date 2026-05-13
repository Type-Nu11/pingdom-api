package com.typenull.pingdom.domain.map.dto;

public record PlaceCreateResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String message
) {
}
