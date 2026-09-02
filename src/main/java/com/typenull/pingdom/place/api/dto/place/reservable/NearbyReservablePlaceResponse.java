package com.typenull.pingdom.place.api.dto.place.reservable;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "현재 위치 주변 예약 가능 장소 페이지 응답")
public record NearbyReservablePlaceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<NearbyReservablePlaceItem> places,
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,
        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext,
        @Schema(format = "date-time", example = "2026-09-02T20:30:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime queriedAt
) {
    public static NearbyReservablePlaceResponse of(
            List<NearbyReservablePlaceItem> places,
            int page,
            int limit,
            long totalElements,
            int totalPages,
            LocalDateTime queriedAt
    ) {
        return new NearbyReservablePlaceResponse(places, page, limit, totalElements, totalPages, page < totalPages,
                queriedAt);
    }
}
