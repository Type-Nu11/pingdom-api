package com.typenull.pingdom.availability.domain;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import io.swagger.v3.oas.annotations.media.Schema;

/** 슬롯별 명시적 가격 및 전액 환불 정책. 금액은 통화의 최소 단위이며 추가 비용은 예약당 한 번 적용. */
public record ReservationTerms(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unitAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long additionalAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer cancellationCutoffMinutes) {
    public ReservationTerms {
        if (unitAmountMinor < 0 || additionalAmountMinor < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
        Currency value = Currency.getInstance(Objects.requireNonNull(currency));
        if (value.getDefaultFractionDigits() < 0) throw new IllegalArgumentException("결제 통화가 필요합니다.");
        ZoneId.of(Objects.requireNonNull(timezone));
        if (cancellable != (cancellationCutoffMinutes != null)
                || cancellationCutoffMinutes != null && cancellationCutoffMinutes < 0) {
            throw new IllegalArgumentException("취소 가능 정책에는 시작 전 취소 기한(분)이 필요합니다.");
        }
    }

    public long totalAmountMinor(int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("인원은 1 이상이어야 합니다.");
        return Math.addExact(Math.multiplyExact(unitAmountMinor, quantity), additionalAmountMinor);
    }
}
