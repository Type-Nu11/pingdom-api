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

    /**
     * 상점 정보에서 예약 링크를 생략할 수 있고 장소 ID가 유지되는지 확인합니다.
     */
    @Test
    void allowsMissingReservationLink() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L, "설명", "02-1234-5678", "https://example.com", null, 99L, NOW
        );

        assertThat(information.getPlaceId()).isEqualTo(10L);
        assertThat(information.getReservationUrl()).isNull();
    }

    /**
     * 설명은 양끝 공백을 제거하고 선택 연락처·웹사이트·예약 URL의 공백은 null로 바꾸는지 확인합니다.
     */
    @Test
    void normalizesOptionalMerchantInformation() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L, "  설명  ", "  ", "  ", "  ", 99L, NOW
        );

        assertThat(information.getDescription()).isEqualTo("설명");
        assertThat(information.getContactPhone()).isNull();
        assertThat(information.getWebsiteUrl()).isNull();
        assertThat(information.getReservationUrl()).isNull();
    }

    /**
     * 새 예약 재고가 ACTIVE 상태와 전체 잔여 인원으로 시작하는지 확인합니다.
     */
    @Test
    void startsWithFullAvailability() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 20, NOW
        );

        assertThat(availability.getStatus()).isEqualTo(
                com.typenull.pingdom.availability.domain.AvailabilityStatus.ACTIVE
        );
        assertThat(availability.getRemainingCapacity()).isEqualTo(20);
    }

    /**
     * 뒤집힌 영업 구간과 0 정원은 재고 생성 시 거절하는지 확인합니다.
     */
    @Test
    void rejectsInvalidAvailabilityBounds() {
        assertThatThrownBy(() -> PlaceAvailability.create(
                99L, 10L, NOW.plusHours(2), NOW, 20, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 0, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 정원 20에서 3명을 예약하면 잔여 정원이 17이 되는지 확인합니다.
     */
    @Test
    void reducesReservedCapacity() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW.plusHours(1), NOW.plusHours(2), 20, NOW
        );

        availability.reserve(3, NOW.plusMinutes(10));

        assertThat(availability.getRemainingCapacity()).isEqualTo(17);
    }

    /**
     * 비활성화한 재고에는 새 예약을 받을 수 없는지 확인합니다.
     */
    @Test
    void rejectsInactiveAvailabilityReservation() {
        PlaceAvailability availability = PlaceAvailability.create(
                99L, 10L, NOW, NOW.plusHours(2), 20, NOW
        );
        availability.deactivate(NOW.plusMinutes(1));

        assertThatThrownBy(() -> availability.reserve(1, NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 새 오퍼가 DRAFT와 발급 수 0으로 시작하는지 확인합니다.
     */
    @Test
    void startsOfferAsDraft() {
        TouristOffer offer = TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW, NOW.plusDays(1), 100, 1, NOW
        );

        assertThat(offer.getStatus()).isEqualTo(com.typenull.pingdom.offer.domain.OfferStatus.DRAFT);
        assertThat(offer.getIssuedQuantity()).isZero();
    }

    /**
     * 종료 전 시각에 발행한 오퍼가 PUBLISHED로 전이하는지 확인합니다.
     */
    @Test
    void publishesOfferBeforeEnd() {
        TouristOffer offer = TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW, NOW.plusDays(1), 100, 1, NOW
        );

        offer.publish(NOW.plusHours(1));

        assertThat(offer.getStatus()).isEqualTo(com.typenull.pingdom.offer.domain.OfferStatus.PUBLISHED);
    }

    /**
     * 종료가 시작보다 앞선 오퍼 기간은 생성 시 거절하는지 확인합니다.
     */
    @Test
    void rejectsReversedOfferPeriod() {
        assertThatThrownBy(() -> TouristOffer.draft(
                99L, 10L, "외국인 전용 혜택", "설명", "10% 할인",
                NOW.plusDays(1), NOW, 100, 1, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 현재보다 늦게 시작하는 이벤트의 일정 상태가 UPCOMING인지 확인합니다.
     */
    @Test
    void identifiesUpcomingEvent() {
        PlaceEvent event = PlaceEvent.create(
                legacyPlace(), "팝업 이벤트", "설명", PlaceEventType.POP_UP,
                NOW.plusHours(1), NOW.plusDays(1), NOW
        );

        assertThat(event.scheduleStatusAt(NOW)).isEqualTo(PlaceEventScheduleStatus.UPCOMING);
    }

    /**
     * 현재가 시작·종료 사이인 이벤트의 일정 상태가 ONGOING인지 확인합니다.
     */
    @Test
    void identifiesOngoingEvent() {
        PlaceEvent event = PlaceEvent.create(
                legacyPlace(), "진행 중 이벤트", "설명", PlaceEventType.POP_UP,
                NOW.minusHours(1), NOW.plusHours(1), NOW.minusHours(1)
        );

        assertThat(event.scheduleStatusAt(NOW)).isEqualTo(PlaceEventScheduleStatus.ONGOING);
    }

    /**
     * 종료 시각이 지난 이벤트의 일정 상태가 ENDED인지 확인합니다.
     */
    @Test
    void identifiesEndedEvent() {
        PlaceEvent event = PlaceEvent.create(
                legacyPlace(), "종료된 이벤트", "설명", PlaceEventType.POP_UP,
                NOW.minusDays(2), NOW.minusDays(1), NOW.minusDays(2)
        );

        assertThat(event.scheduleStatusAt(NOW)).isEqualTo(PlaceEventScheduleStatus.ENDED);
    }

    /**
     * 방문 판단용 이벤트에 연결할 기존 장소 fixture를 만듭니다.
     */
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
