package com.typenull.pingdom.reservation.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tourist_user_id", nullable = false)
    private Long touristUserId;

    @Column(name = "availability_id", nullable = false)
    private Long availabilityId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    private long version;

    public static Reservation create(Long touristUserId, Long availabilityId, String idempotencyKey,
            int quantity, LocalDateTime now) {
        if (quantity <= 0) throw new IllegalArgumentException("예약 인원은 1명 이상이어야 합니다.");
        Reservation reservation = new Reservation();
        reservation.touristUserId = Objects.requireNonNull(touristUserId, "touristUserId must not be null");
        reservation.availabilityId = Objects.requireNonNull(availabilityId, "availabilityId must not be null");
        reservation.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.PENDING;
        reservation.createdAt = Objects.requireNonNull(now, "now must not be null");
        reservation.updatedAt = now;
        return reservation;
    }

    public void confirm(LocalDateTime now) {
        if (status != ReservationStatus.PENDING) throw new IllegalStateException("대기 중인 예약만 확정할 수 있습니다.");
        status = ReservationStatus.CONFIRMED;
        confirmedAt = Objects.requireNonNull(now, "now must not be null");
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status == ReservationStatus.CANCELED) throw new IllegalStateException("이미 취소된 예약입니다.");
        status = ReservationStatus.CANCELED;
        canceledAt = Objects.requireNonNull(now, "now must not be null");
        updatedAt = now;
    }
}
