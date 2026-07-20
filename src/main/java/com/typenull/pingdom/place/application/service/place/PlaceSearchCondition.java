package com.typenull.pingdom.place.application.service.place;

public record PlaceSearchCondition(
        int page,
        int limit,
        String keyword,
        String category,
        String touristCategory,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String sort
) {
}
