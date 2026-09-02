package com.typenull.pingdom.product.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import org.junit.jupiter.api.Test;

class ReservableProductTypeTest {

    @Test
    void supportsOnlyTicketAndClassAndMapsToAvailabilityType() {
        assertThat(ReservableProductType.values())
                .containsExactly(ReservableProductType.TICKET, ReservableProductType.CLASS);
        assertThat(ReservableProductType.TICKET.toAvailabilityProductType())
                .isEqualTo(AvailabilityProductType.TICKET);
        assertThat(ReservableProductType.CLASS.toAvailabilityProductType())
                .isEqualTo(AvailabilityProductType.CLASS);
    }
}
