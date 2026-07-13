package com.typenull.pingdom.place.api.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공개 기간형 이벤트 목록 응답")
public record PlaceEventListResponse(
        List<PlaceEventListItem> events,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static PlaceEventListResponse of(
            List<PlaceEventListItem> events,
            int page,
            int limit,
            long totalCount,
            long totalPages
    ) {
        return new PlaceEventListResponse(events, page, limit, totalCount, totalPages, page < totalPages);
    }
}
