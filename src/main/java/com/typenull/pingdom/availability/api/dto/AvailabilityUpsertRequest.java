package com.typenull.pingdom.availability.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AvailabilityUpsertRequest(
        @NotNull Long placeId,
        @NotNull @Future LocalDateTime startsAt,
        @NotNull @Future LocalDateTime endsAt,
        @Min(1) int totalCapacity
) {
    /** 구버전 상품 예약 요청을 상품 없는 슬롯으로 조용히 등록하지 않는다. */
    @JsonAnySetter
    public void rejectRemovedField(String name, Object value) {
        if ("productId".equals(name) || "productType".equals(name)) {
            throw new IllegalArgumentException("예약 상품 필드는 더 이상 지원하지 않습니다: " + name);
        }
    }
}
