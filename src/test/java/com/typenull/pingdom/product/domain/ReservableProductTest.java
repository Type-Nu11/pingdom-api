package com.typenull.pingdom.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservableProductTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 16, 0);

    @Test
    void ticketProductStartsActive() {
        ReservableProduct product = ReservableProduct.create(
                7L, 3L, AvailabilityProductType.TICKET, "Museum ticket", now);

        assertThat(product.getProductType()).isEqualTo(AvailabilityProductType.TICKET);
        assertThat(product.getStatus()).isEqualTo(ReservableProductStatus.ACTIVE);
    }

    @Test
    void generalTypeCannotBeRegisteredAsProduct() {
        assertThatThrownBy(() -> ReservableProduct.create(
                7L, 3L, AvailabilityProductType.GENERAL, "Legacy", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
