package com.typenull.pingdom.availability.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceAvailabilityTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 19, 10, 0);

    @Test
    void reservesAndReleasesCapacity() {
        PlaceAvailability availability = slot(10);

        availability.reserve(4, now);
        availability.release(2, now);

        assertThat(availability.getRemainingCapacity()).isEqualTo(8);
    }

    @Test
    void rejectsCapacityBelowAlreadyAllocatedQuantity() {
        PlaceAvailability availability = slot(10);
        availability.reserve(6, now);

        assertThatThrownBy(() -> availability.update(now.plusHours(1), now.plusHours(3), 5, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void increasingCapacityPreservesAllocatedQuantity() {
        PlaceAvailability availability = slot(10);
        availability.reserve(4, now);

        availability.update(now.plusHours(1), now.plusHours(3), 15, now);

        assertThat(availability.getRemainingCapacity()).isEqualTo(11);
    }

    @Test
    void inactiveSlotCannotReserve() {
        PlaceAvailability availability = slot(10);
        availability.deactivate(now);

        assertThatThrownBy(() -> availability.reserve(1, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productTypeCanChangeBeforeReservationsExist() {
        PlaceAvailability availability = slot(10);

        availability.update(AvailabilityProductType.CLASS, now.plusHours(1), now.plusHours(3), 10, now);

        assertThat(availability.getProductType()).isEqualTo(AvailabilityProductType.CLASS);
    }

    @Test
    void productTypeCannotChangeAfterCapacityWasAllocated() {
        PlaceAvailability availability = slot(10);
        availability.reserve(1, now);

        assertThatThrownBy(() -> availability.update(
                AvailabilityProductType.TICKET, now.plusHours(1), now.plusHours(3), 10, now))
                .isInstanceOf(IllegalStateException.class);
    }

    private PlaceAvailability slot(int capacity) {
        return PlaceAvailability.create(1L, 2L, now.plusHours(1), now.plusHours(2), capacity, now);
    }
}
