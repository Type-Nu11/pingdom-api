package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import java.time.*;

public record LocationCheckInResponse(Long id, Long placeId, LocalDate checkInDate, Instant observedAt,
        Instant recordedAt, double distanceMeters,
        LocationCheckInStatus status) {
    public static LocationCheckInResponse from(LocationCheckIn checkIn) {
        return new LocationCheckInResponse(checkIn.getId(), checkIn.getPlaceId(), checkIn.getCheckInDate(),
                checkIn.getObservedAt(), checkIn.getRecordedAt(), checkIn.getDistanceMeters(), checkIn.getStatus());
    }
}
