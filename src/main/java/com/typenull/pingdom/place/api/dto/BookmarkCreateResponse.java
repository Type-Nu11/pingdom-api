package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 북마크 생성 응답")
public record BookmarkCreateResponse(
        @Schema(description = "생성한 북마크 ID", example = "1")
        Long id,
        @Schema(description = "생성한 북마크 장소 ID", example = "17")
        Long placeId,
        @Schema(description = "처리 결과 메시지", example = "장소 북마크를 추가했습니다.")
        String message
) {
}
