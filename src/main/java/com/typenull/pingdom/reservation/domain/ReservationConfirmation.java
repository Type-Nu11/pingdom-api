package com.typenull.pingdom.reservation.domain;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;

@Schema(description = "사용자가 확인할 예약 조건. 금액은 최소 통화 단위 정수이며 반올림 없이 단가×인원+예약당 추가 비용으로 계산")
public record ReservationConfirmation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String placeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long availabilityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilityProductType productType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String productName,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime startsAt,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unitAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long additionalAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currencyFractionDigits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean paymentRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellable,
        @com.fasterxml.jackson.annotation.JsonFormat(without = com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OffsetDateTime cancellationDeadline,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "기한 내 취소 수수료. 현재 지원 정책은 0인 전액 환불") long cancellationFeeMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "기한 내 취소 환불액. 취소 불가이면 0") long refundableAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long conditionsVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long productVersion,
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
