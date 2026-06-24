package com.typenull.pingdom.domain.admin.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 장소 목록 조회 응답")
public record AdminMapPlaceResponse(
        List<AdminMapPlaceItem> places,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static AdminMapPlaceResponse of(
            List<AdminMapPlaceItem> places,
            int page,
            int limit,
            long totalCount,
            long totalPages
    ) {
        return new AdminMapPlaceResponse(places, page, limit, totalCount, totalPages, page < totalPages);
    }
}
