package com.typenull.pingdom.reservation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;

@Schema(description = "사용자가 확인할 예약 대상·시간·인원·취소 조건")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationConfirmation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long availabilityId,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime startsAt,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellable,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OffsetDateTime cancellationDeadline,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long conditionsVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt
) {
    public ReservationConfirmation {
        // JSON mapper가 날짜를 UTC로 정규화해도 선언한 표시 시간대와 비교 가능한 스냅샷을 복원.
        var zone = java.time.ZoneId.of(timezone);
        startsAt = startsAt.atZoneSameInstant(zone).toOffsetDateTime();
        endsAt = endsAt.atZoneSameInstant(zone).toOffsetDateTime();
        if (cancellationDeadline != null) {
            cancellationDeadline = cancellationDeadline.atZoneSameInstant(zone).toOffsetDateTime();
        }
    }
}
