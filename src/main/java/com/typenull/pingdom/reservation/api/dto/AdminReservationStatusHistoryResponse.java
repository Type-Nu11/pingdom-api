package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import java.time.LocalDateTime;

public record AdminReservationStatusHistoryResponse(Long id, ReservationStatus status, Long changedBy,
        String reason, LocalDateTime changedAt) {
    public static AdminReservationStatusHistoryResponse from(ReservationStatusHistory history) {
        return new AdminReservationStatusHistoryResponse(history.getId(), history.getStatus(), history.getChangedBy(),
                history.getReason(), history.getChangedAt());
    }
}
