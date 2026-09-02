package com.typenull.pingdom.product.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상점주가 등록할 수 있는 예약 상품 유형")
public enum ReservableProductType {
    TICKET,
    CLASS;

    public AvailabilityProductType toAvailabilityProductType() {
        return AvailabilityProductType.valueOf(name());
    }
}
