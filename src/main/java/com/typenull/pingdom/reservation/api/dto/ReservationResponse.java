package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long touristUserId,
        Long availabilityId,
        int quantity,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt,
        LocalDateTime updatedAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getTouristUserId(),
                reservation.getAvailabilityId(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getCreatedAt(), reservation.getConfirmedAt(), reservation.getCanceledAt(),
                reservation.getUpdatedAt());
    }
}
