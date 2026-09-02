package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import java.time.LocalDateTime;

public record NearbyReservablePlaceCondition(
        int page,
        int limit,
        double latitude,
        double longitude,
        Double radiusKm,
        LocalDateTime from,
        LocalDateTime to,
        Integer quantity,
        AvailabilityProductType productType,
        String category,
        String touristCategory,
        String sort
) {
}
