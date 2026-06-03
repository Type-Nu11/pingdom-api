package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 좌표 생성/확정 응답")
public record PlaceCoordinateCreateResponse(
        @Schema(description = "좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken,

        @Schema(description = "위도", example = "37.5665")
        Double latitude,

        @Schema(description = "경도", example = "126.9780")
        Double longitude
) {
}

