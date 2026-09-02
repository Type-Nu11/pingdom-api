package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminReservationResponse(Long id, ReservationStatus status, int quantity, Long touristUserId,
        String touristUsername, Long merchantOwnerUserId, String merchantOwnerUsername, Long placeId,
        String placeName, Long availabilityId, LocalDateTime reservationStartsAt, LocalDateTime reservationEndsAt,
        String bookerName, String bookerPhone, String requestNote, Long productId, String productName,
        LocalDateTime createdAt, LocalDateTime confirmedAt, Long reviewedBy,
        LocalDateTime reviewedAt, String reviewReason, LocalDateTime rejectedAt, LocalDateTime canceledAt,
        Long canceledBy, List<AdminReservationStatusHistoryResponse> statusHistory) {
    public static AdminReservationResponse of(Reservation reservation, String touristUsername, Long ownerId,
            String ownerUsername, Long placeId, String placeName, String productName,
            List<AdminReservationStatusHistoryResponse> history) {
        return new AdminReservationResponse(reservation.getId(), reservation.getStatus(), reservation.getQuantity(),
                reservation.getTouristUserId(), touristUsername, ownerId, ownerUsername, placeId, placeName,
                reservation.getAvailabilityId(), reservation.getReservationStartsAt(), reservation.getReservationEndsAt(),
                reservation.getBookerName(), reservation.getBookerPhone(), reservation.getRequestNote(),
                reservation.getProductId(), productName,
                reservation.getCreatedAt(), reservation.getConfirmedAt(), reservation.getReviewedBy(),
                reservation.getReviewedAt(), reservation.getReviewReason(), reservation.getRejectedAt(),
                reservation.getCanceledAt(), reservation.getCanceledBy(), history);
    }
}
