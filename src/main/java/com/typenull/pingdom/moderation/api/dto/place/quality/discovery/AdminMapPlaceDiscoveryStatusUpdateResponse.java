package com.typenull.pingdom.moderation.api.dto.place.quality.discovery;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 탐색 노출 상태 수정 응답")
public record AdminMapPlaceDiscoveryStatusUpdateResponse(
        Long placeId,
        @Schema(
                description = "탐색 노출 상태. VISIBLE은 공개 탐색·자동완성·북마크 목록·추천 후보에 노출되고, HIDDEN은 제외됩니다.",
                example = "HIDDEN"
        )
        PlaceDiscoveryStatus discoveryStatus,
        String message
) {
}
