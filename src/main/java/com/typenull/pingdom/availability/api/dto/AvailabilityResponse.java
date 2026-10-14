package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "예약 가능 시간 응답")
public record AvailabilityResponse(
        @Schema(example = "77", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(example = "70069", requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @Schema(format = "date-time", example = "2026-09-10T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startsAt,
        @Schema(format = "date-time", example = "2026-09-10T17:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime endsAt,
        @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCapacity,
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        int remainingCapacity,
        @Schema(example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        AvailabilityStatus status,
        @Schema(description = "시간대·취소 조건 변경 버전. 조건이 미설정이어도 반환합니다.", example = "1", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long conditionsVersion,
        @Schema(description = "저장된 시간대·취소 조건. null은 미설정이며 취소 불가를 뜻하지 않습니다.",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        ReservationTermsResponse reservationTerms
) {
    public static AvailabilityResponse from(PlaceAvailability availability) {
        return new AvailabilityResponse(availability.getId(), availability.getPlaceId(),
                availability.getStartsAt(),
                availability.getEndsAt(), availability.getTotalCapacity(), availability.getRemainingCapacity(),
                availability.getStatus(), availability.getConditionsVersion(),
                availability.getReservationTerms() == null
                        ? null
                        : ReservationTermsResponse.from(availability.getReservationTerms()));
    }
}
