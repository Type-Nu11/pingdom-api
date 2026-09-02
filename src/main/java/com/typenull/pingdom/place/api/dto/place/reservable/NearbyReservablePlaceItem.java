package com.typenull.pingdom.place.api.dto.place.reservable;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "현재 위치 주변 예약 가능 장소 요약")
public record NearbyReservablePlaceItem(
        @Schema(example = "70069", requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @Schema(example = "이월드", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(example = "테마파크", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(example = "35.8532", requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,
        @Schema(example = "128.5638", requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude,
        @Schema(example = "대구광역시 달서구 두류공원로 200", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String address,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(example = "430", requiredMode = Schema.RequiredMode.REQUIRED)
        long distanceMeters,
        @Schema(example = "77", requiredMode = Schema.RequiredMode.REQUIRED)
        Long availabilityId,
        @Schema(format = "date-time", example = "2026-09-10T14:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime availableStartsAt,
        @Schema(format = "date-time", example = "2026-09-10T17:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime availableEndsAt,
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        int remainingCapacity,
        @Schema(example = "TICKET", requiredMode = Schema.RequiredMode.REQUIRED)
        AvailabilityProductType productType,
        @Schema(description = "GENERAL은 null입니다.", example = "12", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,
        @Schema(description = "GENERAL은 null입니다.", example = "이월드 오후 입장권", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String productName
) {
}
