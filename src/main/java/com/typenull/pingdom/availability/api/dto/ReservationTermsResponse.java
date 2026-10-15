package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.ReservationTerms;
import io.swagger.v3.oas.annotations.media.Schema;

/** 상점주가 저장한 슬롯별 시간대·취소 조건. 조건이 없는 슬롯은 AvailabilityResponse에서 null로 표현한다. */
@Schema(description = "저장된 예약 시간대·취소 조건")
public record ReservationTermsResponse(
        @Schema(description = "IANA 시간대", example = "Asia/Seoul", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cancellable,
        @Schema(description = "취소 가능할 때만 제공하는 시작 전 취소 기한(분)", example = "60", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer cancellationCutoffMinutes
) {
    public static ReservationTermsResponse from(ReservationTerms terms) {
        return new ReservationTermsResponse(terms.timezone(), terms.cancellable(), terms.cancellationCutoffMinutes());
    }
}
