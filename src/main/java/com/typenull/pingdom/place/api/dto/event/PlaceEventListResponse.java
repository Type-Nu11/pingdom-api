package com.typenull.pingdom.place.api.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공개 기간형 이벤트 목록 응답")
public record PlaceEventListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlaceEventListItem> events,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42") long totalCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") long totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean hasNext
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
