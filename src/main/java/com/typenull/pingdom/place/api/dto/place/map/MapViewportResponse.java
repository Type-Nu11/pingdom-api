package com.typenull.pingdom.place.api.dto.place.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 줌 단계에 따른 마커 또는 클러스터 응답입니다.
 * 사용하지 않는 목록은 빈 목록이고 truncated는 최대 개수 제한으로 결과가 잘렸음을 나타냅니다.
 */
public record MapViewportResponse(
        @Schema(
                description = "zoom에 따른 응답 표현 방식",
                allowableValues = {"MARKERS", "CLUSTERS"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String mode,
        @Schema(description = "요청한 지도 zoom 단계", minimum = "0", maximum = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int zoom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MapClusterItem> clusters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MapMarkerItem> markers,
        @Schema(description = "최대 500개 결과로 잘렸는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated
) {
}
