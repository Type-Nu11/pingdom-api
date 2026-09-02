package com.typenull.pingdom.place.api.dto.place.reservable;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "현재 위치 주변 예약 가능 장소 페이지 응답")
public record NearbyReservablePlaceResponse(
        List<NearbyReservablePlaceItem> places,
        int page,
        int limit,
        long totalElements,
        long totalPages,
        boolean hasNext,
        LocalDateTime queriedAt
) {
    public static NearbyReservablePlaceResponse of(
            List<NearbyReservablePlaceItem> places,
            int page,
            int limit,
            long totalElements,
            long totalPages,
            LocalDateTime queriedAt
    ) {
        return new NearbyReservablePlaceResponse(places, page, limit, totalElements, totalPages, page < totalPages,
                queriedAt);
    }
}
