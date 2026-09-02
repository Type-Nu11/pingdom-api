package com.typenull.pingdom.place.api.dto.place.reservable;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "현재 위치 주변 예약 가능 장소 요약")
public record NearbyReservablePlaceItem(
        Long placeId,
        String name,
        String category,
        Double latitude,
        Double longitude,
        String address,
        String imageUrl,
        long distanceMeters,
        Long availabilityId,
        LocalDateTime availableStartsAt,
        LocalDateTime availableEndsAt,
        int remainingCapacity,
        AvailabilityProductType productType,
        Long productId,
        String productName
) {
}
