package com.typenull.pingdom.reservation.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservationTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 15, 0);

    /**
     * 예약 생성 시 PENDING 상태·요청 수량 3과 미설정 확정·취소 시각을 보유하는지 검증.
     */
    @Test
    void createdReservationStartsPending() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 3, now);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getQuantity()).isEqualTo(3);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCanceledAt()).isNull();
    }

    /**
     * 대기 예약을 5분 뒤 확정하면 CONFIRMED 상태와 확정 시각이 기록되는지 검증.
     */
    @Test
    void pendingReservationCanBeConfirmed() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);

        reservation.confirm(now.plusMinutes(5));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(now.plusMinutes(5));
    }

    /**
     * 확정 예약을 취소하면 CANCELED 상태와 취소 시각이 기록되는지 검증.
     */
    @Test
    void confirmedReservationCanBeCanceled() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);
        reservation.confirm(now.plusMinutes(5));

        reservation.cancel(now.plusMinutes(10));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(reservation.getCanceledAt()).isEqualTo(now.plusMinutes(10));
    }

    /**
     * 취소 예약을 다시 확정하거나 취소하면 각각 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void canceledReservationCannotTransitionAgain() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);
        reservation.cancel(now.plusMinutes(5));

        assertThatThrownBy(() -> reservation.confirm(now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reservation.cancel(now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 수량 0으로 예약을 생성하면 IllegalArgumentException이 발생하는지 검증.
     */
    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> Reservation.create(1L, 2L, "key", 0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
