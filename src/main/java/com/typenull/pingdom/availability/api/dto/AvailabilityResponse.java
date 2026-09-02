package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
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
        @Schema(description = "GENERAL은 null이며 TICKET/CLASS는 상품 ID입니다.", example = "12",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,
        @Schema(description = "예약 대상 유형", example = "TICKET", requiredMode = Schema.RequiredMode.REQUIRED)
        AvailabilityProductType productType,
        @Schema(description = "GENERAL은 null이며 TICKET/CLASS는 상품명입니다.", example = "이월드 오후 입장권",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String productName,
        @Schema(format = "date-time", example = "2026-09-10T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startsAt,
        @Schema(format = "date-time", example = "2026-09-10T17:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime endsAt,
        @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCapacity,
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        int remainingCapacity,
        @Schema(example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        AvailabilityStatus status
) {
    public static AvailabilityResponse from(PlaceAvailability availability, String productName) {
        return new AvailabilityResponse(availability.getId(), availability.getPlaceId(), availability.getProductId(),
                availability.getProductType(), productName,
                availability.getStartsAt(),
                availability.getEndsAt(), availability.getTotalCapacity(), availability.getRemainingCapacity(),
                availability.getStatus());
    }
}
