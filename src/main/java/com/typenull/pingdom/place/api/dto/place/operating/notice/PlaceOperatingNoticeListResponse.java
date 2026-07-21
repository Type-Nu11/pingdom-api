package com.typenull.pingdom.place.api.dto.place.operating.notice;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "상점 운영 상태 공지 목록 응답")
public record PlaceOperatingNoticeListResponse(
        Long placeId,
        boolean currentlyOperating,
        LocalDateTime checkedAt,
        List<PlaceOperatingNoticeResponse> notices
) {
}
