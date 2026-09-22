package com.typenull.pingdom.place.domain.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceConversionEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    /** 예약과 혜택 전환을 서로 다른 유형으로 보존하고 고정 발생 시각을 유지하는지 확인. */
    @Test
    void distinguishesReservationAndBenefit() {
        PlaceConversionEvent reservation = event(PlaceConversionEventType.RESERVATION, 101L);
        PlaceConversionEvent benefit = event(PlaceConversionEventType.BENEFIT, 202L);

        assertThat(reservation.getConversionType()).isEqualTo(PlaceConversionEventType.RESERVATION);
        assertThat(benefit.getConversionType()).isEqualTo(PlaceConversionEventType.BENEFIT);
        assertThat(reservation.getOccurredAt()).isEqualTo(NOW);
    }

    /** 원본 식별자 0과 공백 중복 키를 각각 거부해 식별 불가능한 전환 생성을 막는지 확인. */
    @Test
    void rejectsInvalidConversionIdentity() {
        assertThatThrownBy(() -> event(PlaceConversionEventType.RESERVATION, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceConversionEvent.create(
                1L, 2L, PlaceConversionEventType.BENEFIT, 3L, " ", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 전환 유형·원본 ID로 중복 키를 만들고 발생·생성 시각을 고정. */
    private PlaceConversionEvent event(PlaceConversionEventType type, Long sourceId) {
        return PlaceConversionEvent.create(10L, 20L, type, sourceId, type + ":" + sourceId, NOW, NOW);
    }
}
