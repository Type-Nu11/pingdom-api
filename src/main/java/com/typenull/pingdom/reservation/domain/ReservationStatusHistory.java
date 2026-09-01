package com.typenull.pingdom.reservation.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "reservation_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reservation_id", nullable = false) private Long reservationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReservationStatus status;
    @Column(name = "changed_by") private Long changedBy;
    @Column(length = 500) private String reason;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;

    public static ReservationStatusHistory of(Long reservationId, ReservationStatus status, Long changedBy,
            String reason, LocalDateTime changedAt) {
        ReservationStatusHistory history = new ReservationStatusHistory();
        history.reservationId = reservationId;
        history.status = status;
        history.changedBy = changedBy;
        history.reason = reason;
        history.changedAt = changedAt;
        return history;
    }
}
