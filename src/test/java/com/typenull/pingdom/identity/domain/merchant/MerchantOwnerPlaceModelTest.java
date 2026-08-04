package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantOwnerPlaceModelTest {

    @Test
    void defaultsOperationalQualityToUnmeasuredAndZeroRates() {
        MerchantOwnerPlace place = place();

        assertThat(place.getOperationalQualityStatus()).isEqualTo(MerchantOperationalQualityStatus.UNMEASURED);
        assertThat(place.getReservationResponseRate()).isZero();
        assertThat(place.getReservationCancellationRate()).isZero();
        assertThat(place.getNoShowRate()).isZero();
        assertThat(place.getQualityEvaluatedAt()).isNull();
    }

    @Test
    void acceptsZeroAndHundredPercentQualityRates() {
        MerchantOwnerPlace place = place();

        place.updateOperationalQuality(
                MerchantOperationalQualityStatus.HEALTHY,
                0,
                100,
                0,
                LocalDateTime.of(2026, 8, 4, 12, 0)
        );

        assertThat(place.getOperationalQualityStatus()).isEqualTo(MerchantOperationalQualityStatus.HEALTHY);
        assertThat(place.getReservationResponseRate()).isZero();
        assertThat(place.getReservationCancellationRate()).isEqualTo(100);
        assertThat(place.getNoShowRate()).isZero();
        assertThat(place.getQualityEvaluatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 12, 0));
    }

    @Test
    void rejectsQualityRatesOutsideDatabaseConstraintRange() {
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, -1, 0, 0, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, 0, 101, 0, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, 0, 0, 101, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesAllPersistedOperationalQualityStatuses() {
        assertThat(MerchantOperationalQualityStatus.values())
                .containsExactly(
                        MerchantOperationalQualityStatus.UNMEASURED,
                        MerchantOperationalQualityStatus.HEALTHY,
                        MerchantOperationalQualityStatus.NEEDS_ATTENTION,
                        MerchantOperationalQualityStatus.AT_RISK
                );
    }

    private MerchantOwnerPlace place() {
        return MerchantOwnerPlace.builder()
                .placeId(1L)
                .merchantOwnerUserId(2L)
                .createdAt(LocalDateTime.of(2026, 8, 4, 10, 0))
                .build();
    }
}
