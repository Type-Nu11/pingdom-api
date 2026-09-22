package com.typenull.pingdom.product.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import org.junit.jupiter.api.Test;

class ReservableProductTypeTest {

    /**
     * 상품 유형이 TICKET과 CLASS만 제공하고 각각 같은 AvailabilityProductType으로 변환되는지 검증.
     * 요청 DTO에 GENERAL이 노출되거나 예약 유형으로 잘못 매핑되는 회귀를 방지.
     */
    @Test
    void mapsSupportedProductTypes() {
        assertThat(ReservableProductType.values())
                .containsExactly(ReservableProductType.TICKET, ReservableProductType.CLASS);
        assertThat(ReservableProductType.TICKET.toAvailabilityProductType())
                .isEqualTo(AvailabilityProductType.TICKET);
        assertThat(ReservableProductType.CLASS.toAvailabilityProductType())
                .isEqualTo(AvailabilityProductType.CLASS);
    }
}
