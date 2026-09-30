package com.typenull.pingdom.reservation.api.dto;

import com.typenull.pingdom.reservation.domain.ReservationConfirmation;
import com.typenull.pingdom.reservation.domain.ReservationQuote;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationQuoteResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "본인 예약 생성에만 사용 가능한 불투명 토큰. 같은 토큰은 하나의 예약 의도에만 사용") String confirmationToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationConfirmation confirmation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingCapacity
) {
    public static ReservationQuoteResponse from(ReservationQuote quote, int remainingCapacity) {
        return new ReservationQuoteResponse(quote.getId(), quote.getConfirmation(), remainingCapacity);
    }
}
