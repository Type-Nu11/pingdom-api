package com.typenull.pingdom.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservableProductTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 16, 0);

    /**
     * TICKET 상품을 생성하면 상품 유형을 유지하고 ACTIVE 상태로 시작하는지 검증한다.
     * 초기 상태가 비활성으로 저장되어 예약에서 제외되는 회귀를 방지한다.
     */
    @Test
    void ticketProductStartsActive() {
        ReservableProduct product = ReservableProduct.create(
                7L, 3L, AvailabilityProductType.TICKET, "Museum ticket", now);

        assertThat(product.getProductType()).isEqualTo(AvailabilityProductType.TICKET);
        assertThat(product.getStatus()).isEqualTo(ReservableProductStatus.ACTIVE);
    }

    /**
     * GENERAL 유형으로 상품을 생성하면 IllegalArgumentException이 발생하는지 검증한다.
     * 일반 예약 슬롯용 유형이 판매 상품으로 등록되는 것을 방지한다.
     */
    @Test
    void rejectsGeneralProduct() {
        assertThatThrownBy(() -> ReservableProduct.create(
                7L, 3L, AvailabilityProductType.GENERAL, "Legacy", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
