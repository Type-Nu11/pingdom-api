package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "장소 즐겨찾기 생성 요청")
public record FavoriteCreateRequest(
        @NotNull(message = "placeId는 필수입니다.")
        @Schema(description = "즐겨찾기에 추가할 장소 ID", example = "17")
        Long placeId
) {
}
