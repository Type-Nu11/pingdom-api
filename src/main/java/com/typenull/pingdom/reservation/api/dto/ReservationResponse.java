package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관광객 예약 응답")
public record ReservationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long touristUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long availabilityId,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "상품 스냅샷이 없는 예약에서는 null입니다."
        )
        Long productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilityProductType productType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "예약이 CONFIRMED 상태가 되기 전에는 null입니다."
        )
        LocalDateTime confirmedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        LocalDateTime rejectedAt,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "예약이 CANCELED 상태가 되기 전에는 null입니다."
        )
        LocalDateTime canceledAt,
        Long canceledBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getTouristUserId(),
                reservation.getAvailabilityId(), reservation.getProductId(), reservation.getProductType(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(), reservation.getConfirmedAt(), reservation.getReviewedBy(),
                reservation.getReviewedAt(), reservation.getReviewReason(), reservation.getRejectedAt(), reservation.getCanceledAt(), reservation.getCanceledBy(),
                reservation.getUpdatedAt());
    }
}
