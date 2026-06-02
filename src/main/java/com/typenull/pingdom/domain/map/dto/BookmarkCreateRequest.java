package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "장소 북마크 생성 요청")
public record BookmarkCreateRequest(
        @NotNull(message = "placeId는 필수입니다.")
        @Schema(description = "북마크에 추가할 장소 ID", example = "17")
        Long placeId
) {
}
