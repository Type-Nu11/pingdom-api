package com.typenull.pingdom.availability.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZoneId;
import java.util.Objects;

/** 슬롯별 명시적 시간대와 취소 정책. 미설정 정책은 임의로 추정하지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationTerms(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer cancellationCutoffMinutes) {
    public ReservationTerms {
        ZoneId.of(Objects.requireNonNull(timezone));
        if (cancellable != (cancellationCutoffMinutes != null)
                || cancellationCutoffMinutes != null && cancellationCutoffMinutes < 0) {
            throw new IllegalArgumentException("취소 가능 정책에는 시작 전 취소 기한(분)이 필요합니다.");
        }
    }
}
