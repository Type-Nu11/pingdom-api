package com.typenull.pingdom.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
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

    @Test
    void availabilityReservationReducesRemainingCapacity() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 20, NOW
        );

        availability.reserve(3, NOW.plusMinutes(10));

        assertThat(availability.getRemainingCapacity()).isEqualTo(17);
    }

    @Test
    void inactiveAvailabilityCannotBeReserved() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 20, NOW
        );
        availability.deactivate(NOW.plusMinutes(1));

        assertThatThrownBy(() -> availability.reserve(1, NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void touristOfferStartsAsDraftUntilPublished() {
        TouristOffer offer = TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW, NOW.plusDays(1), 100, 1, NOW
        );

        assertThat(offer.getStatus()).isEqualTo(com.typenull.pingdom.offer.domain.OfferStatus.DRAFT);
        assertThat(offer.getIssuedQuantity()).isZero();
    }

    @Test
    void touristOfferCanBePublishedOnlyBeforeItsEndTime() {
        TouristOffer offer = TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW, NOW.plusDays(1), 100, 1, NOW
        );

        offer.publish(NOW.plusHours(1));

        assertThat(offer.getStatus()).isEqualTo(com.typenull.pingdom.offer.domain.OfferStatus.PUBLISHED);
    }

    @Test
    void touristOfferRejectsAnInvalidVisitDecisionPeriod() {
        assertThatThrownBy(() -> TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW.plusDays(1), NOW, 100, 1, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeEventExposesUpcomingScheduleForVisitDecision() {
        PlaceEvent event = PlaceEvent.create(
                legacyPlace(), "팝업 이벤트", "설명", PlaceEventType.POPUP,
                NOW.plusHours(1), NOW.plusDays(1), NOW
        );

        assertThat(event.scheduleStatusAt(NOW)).isEqualTo(PlaceEventScheduleStatus.UPCOMING);
    }

    @Test
    void placeEventExposesOngoingScheduleForVisitDecision() {
        PlaceEvent event = PlaceEvent.create(
                legacyPlace(), "진행 중 이벤트", "설명", PlaceEventType.POPUP,
                NOW.minusHours(1), NOW.plusHours(1), NOW.minusHours(1)
        );

        assertThat(event.scheduleStatusAt(NOW)).isEqualTo(PlaceEventScheduleStatus.ONGOING);
    }

    private MapPlace legacyPlace() {
        return MapPlace.builder()
                .id(10L)
                .name("장소")
                .address("서울시 중구 테스트로 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .registrant("merchant")
                .build();
    }
}
