package com.typenull.pingdom.availability.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.typenull.pingdom.availability.domain.ReservationTerms;
import jakarta.validation.constraints.*;

public record ReservationTermsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Boolean cancellable,
        @Min(0) Integer cancellationCutoffMinutes
) {
    /** 구버전 금액 설정 요청을 취소 조건만 저장한 성공 응답으로 처리하지 않는다. */
    @JsonAnySetter
    public void rejectRemovedField(String name, Object value) {
        if ("unitAmountMinor".equals(name) || "additionalAmountMinor".equals(name) || "currency".equals(name)) {
            throw new IllegalArgumentException("예약 금액 필드는 더 이상 지원하지 않습니다: " + name);
        }
    }

    public ReservationTerms toTerms() {
        return new ReservationTerms(timezone,
                cancellable, cancellationCutoffMinutes);
    }
}
