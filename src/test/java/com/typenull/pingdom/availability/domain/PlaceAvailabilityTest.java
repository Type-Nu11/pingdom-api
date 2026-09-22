package com.typenull.pingdom.availability.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceAvailabilityTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 19, 10, 0);

    /**
     * 정원 10인 슬롯에서 4명을 예약하고 2명을 해제하면 잔여 정원이 8인지 검증.
     * 예약과 해제 시 수량이 반대로 반영되는 회귀를 방지.
     */
    @Test
    void reservesAndReleasesCapacity() {
        PlaceAvailability availability = slot(10);

        availability.reserve(4, now);
        availability.release(2, now);

        assertThat(availability.getRemainingCapacity()).isEqualTo(8);
    }

    /**
     * 6명을 예약한 슬롯의 총 정원을 5로 낮추면 IllegalStateException이 발생하는지 검증.
     * 기존 예약 수보다 작은 정원으로 변경되는 것을 방지.
     */
    @Test
    void rejectsCapacityBelowReservations() {
        PlaceAvailability availability = slot(10);
        availability.reserve(6, now);

        assertThatThrownBy(() -> availability.update(now.plusHours(1), now.plusHours(3), 5, now))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 4명을 예약한 뒤 총 정원을 10에서 15로 늘리면 잔여 정원이 11인지 검증.
     * 정원 변경이 이미 배정된 예약 수를 초기화하는 회귀를 방지.
     */
    @Test
    void increasingCapacityPreservesAllocatedQuantity() {
        PlaceAvailability availability = slot(10);
        availability.reserve(4, now);

        availability.update(now.plusHours(1), now.plusHours(2), 15, now);

        assertThat(availability.getRemainingCapacity()).isEqualTo(11);
    }

    /**
     * 비활성화한 슬롯에서 예약을 시도하면 IllegalStateException이 발생하는지 검증.
     * 판매를 중단한 슬롯의 추가 예약을 방지.
     */
    @Test
    void inactiveSlotCannotReserve() {
        PlaceAvailability availability = slot(10);
        availability.deactivate(now);

        assertThatThrownBy(() -> availability.reserve(1, now))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 예약이 없는 슬롯을 CLASS 유형으로 수정하면 변경된 유형을 보유하는지 검증.
     */
    @Test
    void changesUnreservedProductType() {
        PlaceAvailability availability = slot(10);

        availability.update(AvailabilityProductType.CLASS, now.plusHours(1), now.plusHours(3), 10, now);

        assertThat(availability.getProductType()).isEqualTo(AvailabilityProductType.CLASS);
    }

    /**
     * 1명을 예약한 슬롯의 상품 유형을 TICKET으로 바꾸면 IllegalStateException이 발생하는지 검증.
     * 기존 예약이 다른 유형의 상품으로 바뀌는 것을 방지.
     */
    @Test
    void rejectsReservedProductTypeChange() {
        PlaceAvailability availability = slot(10);
        availability.reserve(1, now);

        assertThatThrownBy(() -> availability.update(
                AvailabilityProductType.TICKET, now.plusHours(1), now.plusHours(3), 10, now))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 1명을 예약한 슬롯의 시작 시간을 바꾸면 IllegalStateException이 발생하는지 검증.
     * 확정된 예약 시간의 사후 변경을 방지.
     */
    @Test
    void rejectsReservedTimeChange() {
        PlaceAvailability availability = slot(10);
        availability.reserve(1, now);

        assertThatThrownBy(() -> availability.update(
                now.plusHours(2), now.plusHours(3), 10, now))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 슬롯의 시작 시각과 같은 시각에 예약하면 IllegalStateException이 발생하는지 검증.
     * 예약 가능 시간의 경계가 시작 시각을 포함하지 않도록 고정.
     */
    @Test
    void startedSlotCannotReserve() {
        PlaceAvailability availability = slot(10);

        assertThatThrownBy(() -> availability.reserve(1, now.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 고정된 현재 시각에서 한 시간 뒤 시작하는 슬롯을 주어진 정원으로 생성.
     * 각 테스트가 예약 수량과 상태 변화만 설정할 수 있도록 시간 조건을 공통화.
     */
    private PlaceAvailability slot(int capacity) {
        return PlaceAvailability.create(1L, 2L, now.plusHours(1), now.plusHours(2), capacity, now);
    }
}
