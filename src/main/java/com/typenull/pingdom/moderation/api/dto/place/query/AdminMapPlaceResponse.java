package com.typenull.pingdom.moderation.api.dto.place.query;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 장소 목록 조회 응답")
public record AdminMapPlaceResponse(
        List<AdminMapPlaceItem> places,
        int page,
        int limit,
        long totalCount,
        @Schema(description = "전체 페이지 수. 조회 결과가 없어도 1을 반환합니다.", minimum = "1", example = "1")
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
        long normalizedTotalPages = Math.max(totalPages, 1);
        return new AdminMapPlaceResponse(
                places,
                page,
                limit,
                totalCount,
                normalizedTotalPages,
                page < normalizedTotalPages
        );
    }
}
