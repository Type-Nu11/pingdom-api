package com.typenull.pingdom.place.api.dto.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 북마크 삭제 응답")
public record BookmarkRemoveResponse(
        @Schema(description = "삭제한 북마크 유저 ID", example = "1")
        Long userId,
        @Schema(description = "삭제한 북마크 장소 ID", example = "17")
        Long placeId,
        @Schema(description = "처리 결과 메시지", example = "장소 북마크를 삭제했습니다.")
        String message
) {
}
