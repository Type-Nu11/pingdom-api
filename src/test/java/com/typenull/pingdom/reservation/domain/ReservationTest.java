package com.typenull.pingdom.reservation.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservationTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 15, 0);

    @Test
    void createdReservationStartsPending() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 3, now);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getQuantity()).isEqualTo(3);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCanceledAt()).isNull();
    }

    @Test
    void pendingReservationCanBeConfirmed() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);

        reservation.confirm(now.plusMinutes(5));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    void confirmedReservationCanBeCanceled() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);
        reservation.confirm(now.plusMinutes(5));

        reservation.cancel(now.plusMinutes(10));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(reservation.getCanceledAt()).isEqualTo(now.plusMinutes(10));
    }

    @Test
    void canceledReservationCannotTransitionAgain() {
        Reservation reservation = Reservation.create(1L, 2L, "key", 1, now);
        reservation.cancel(now.plusMinutes(5));

        assertThatThrownBy(() -> reservation.confirm(now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reservation.cancel(now.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> Reservation.create(1L, 2L, "key", 0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
