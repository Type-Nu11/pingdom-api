package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import java.time.LocalDateTime;

public record AvailabilityResponse(Long id, Long placeId, LocalDateTime startsAt, LocalDateTime endsAt,
                                   int totalCapacity, int remainingCapacity, AvailabilityStatus status) {
    public static AvailabilityResponse from(PlaceAvailability availability) {
        return new AvailabilityResponse(availability.getId(), availability.getPlaceId(), availability.getStartsAt(),
                availability.getEndsAt(), availability.getTotalCapacity(), availability.getRemainingCapacity(),
                availability.getStatus());
    }
}
