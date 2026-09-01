package com.typenull.pingdom.reservation.api.dto;

import java.util.List;

public record AdminReservationPageResponse(List<AdminReservationResponse> reservations, int page, int limit,
        long totalElements, int totalPages, boolean hasNext) {}
