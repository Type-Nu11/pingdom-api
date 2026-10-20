package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import java.time.LocalDateTime;

public record ReservationStatusHistoryResponse(Long id, ReservationStatus status, Long changedBy,
        String reason, LocalDateTime changedAt) {
    public static ReservationStatusHistoryResponse from(ReservationStatusHistory history) {
        return new ReservationStatusHistoryResponse(history.getId(), history.getStatus(), history.getChangedBy(),
                history.getReason(), history.getChangedAt());
    }
}
