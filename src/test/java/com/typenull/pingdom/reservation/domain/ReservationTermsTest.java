package com.typenull.pingdom.reservation.domain;

import static org.assertj.core.api.Assertions.*;
import com.typenull.pingdom.availability.domain.ReservationTerms;
import org.junit.jupiter.api.Test;

class ReservationTermsTest {
    @Test
    void requiresExplicitTimezoneAndCancellationPolicy() {
        assertThatThrownBy(() -> new ReservationTerms("UTC", true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms("UTC", false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms("UTC", true, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationTerms("invalid/zone", false, null)).isInstanceOf(java.time.DateTimeException.class);
        assertThat(new ReservationTerms("UTC", true, 0).cancellationCutoffMinutes()).isZero();
    }
}
