package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 목록 조회 응답")
public record PlaceListResponse(
        List<PlaceListItem> places,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static PlaceListResponse of(
            List<PlaceListItem> places,
            int page,
            int limit,
            long totalCount,
            long totalPages
    ) {
        return new PlaceListResponse(places, page, limit, totalCount, totalPages, page < totalPages);
    }
}
