package com.typenull.pingdom.reservation.domain;

import static org.assertj.core.api.Assertions.*;
import com.typenull.pingdom.availability.domain.ReservationTerms;
import org.junit.jupiter.api.Test;

class ReservationTermsTest {
    @Test
    void requiresExplicitSupportedCurrencyTimezoneAndCancellationPolicy() {
        assertThatThrownBy(() -> new ReservationTerms(-1, 0, "KRW", "UTC", false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms(0, 0, "XXX", "UTC", false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms(0, 0, "KRW", "UTC", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms(0, 0, "KRW", "UTC", false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms(0, 0, "KRW", "invalid/zone", false, null)).isInstanceOf(java.time.DateTimeException.class);
    }

    @Test
    void computesMinorUnitsWithoutRoundingAndRejectsOverflow() {
        assertThat(new ReservationTerms(199, 50, "USD", "UTC", true, 0).totalAmountMinor(3)).isEqualTo(647);
        assertThatThrownBy(() -> new ReservationTerms(Long.MAX_VALUE, 0, "USD", "UTC", false, null).totalAmountMinor(2))
                .isInstanceOf(ArithmeticException.class);
    }
}
