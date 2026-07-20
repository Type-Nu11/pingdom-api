package com.typenull.pingdom.reservation.api.dto;

import java.util.List;

public record ReservationPageResponse(
        List<ReservationResponse> reservations,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
