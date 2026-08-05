package com.typenull.pingdom.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionSourceModelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Test
    void merchantInformationAllowsOptionalReservationLink() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L, "설명", "02-1234-5678", "https://example.com", null, 99L, NOW
        );

        assertThat(information.getPlaceId()).isEqualTo(10L);
        assertThat(information.getReservationUrl()).isNull();
    }

    @Test
    void merchantInformationNormalizesBlankOptionalValues() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L, "  설명  ", "  ", "  ", "  ", 99L, NOW
        );

        assertThat(information.getDescription()).isEqualTo("설명");
        assertThat(information.getContactPhone()).isNull();
        assertThat(information.getWebsiteUrl()).isNull();
        assertThat(information.getReservationUrl()).isNull();
    }

    @Test
    void availabilityStartsActiveWithFullCapacity() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 20, NOW
        );

        assertThat(availability.getStatus()).isEqualTo(
                com.typenull.pingdom.availability.domain.AvailabilityStatus.ACTIVE
        );
        assertThat(availability.getRemainingCapacity()).isEqualTo(20);
    }

    @Test
    void availabilityRejectsNonPositivePeriodAndCapacity() {
        assertThatThrownBy(() -> PlaceAvailability.create(
                99L, 10L, NOW.plusHours(2), NOW, 20, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 0, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
