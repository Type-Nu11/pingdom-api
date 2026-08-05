package com.typenull.pingdom.place.domain.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceConversionEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Test
    void recordsReservationAndBenefitAsDistinctConversionTypes() {
        PlaceConversionEvent reservation = event(PlaceConversionEventType.RESERVATION, 101L);
        PlaceConversionEvent benefit = event(PlaceConversionEventType.BENEFIT, 202L);

        assertThat(reservation.getConversionType()).isEqualTo(PlaceConversionEventType.RESERVATION);
        assertThat(benefit.getConversionType()).isEqualTo(PlaceConversionEventType.BENEFIT);
        assertThat(reservation.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidIdentifiersAndDeduplicationKey() {
        assertThatThrownBy(() -> event(PlaceConversionEventType.RESERVATION, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceConversionEvent.create(
                1L, 2L, PlaceConversionEventType.BENEFIT, 3L, " ", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PlaceConversionEvent event(PlaceConversionEventType type, Long sourceId) {
        return PlaceConversionEvent.create(10L, 20L, type, sourceId, type + ":" + sourceId, NOW, NOW);
    }
}
