package com.typenull.pingdom.place.api.dto.coordinate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 좌표 생성/확정 응답")
public record PlaceCoordinateCreateResponse(
        @Schema(description = "좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken,

        @Schema(description = "카카오 장소 ID", example = "27414316")
        String kakaoPlaceId
) {
}
