package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 즐겨찾기 생성 응답")
public record FavoriteCreateResponse(
        @Schema(description = "즐겨찾기 ID", example = "1")
        Long id,
        @Schema(description = "즐겨찾기한 장소 ID", example = "17")
        Long placeId,
        @Schema(description = "처리 결과 메시지", example = "장소 즐겨찾기를 추가했습니다.")
        String message
) {
}
