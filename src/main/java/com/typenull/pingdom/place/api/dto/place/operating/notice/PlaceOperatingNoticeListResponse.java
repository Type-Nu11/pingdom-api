package com.typenull.pingdom.place.api.dto.place.operating.notice;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "상점 운영 상태 공지 목록 응답")
public record PlaceOperatingNoticeListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean currentlyOperating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime checkedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceOperatingNoticeResponse> notices
) {
}
