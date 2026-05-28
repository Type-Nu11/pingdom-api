package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "장소 업로드 요청(좌표 토큰 기반)")
public record PlaceUploadRequest(
        @NotBlank(message = "장소 이름은 필수입니다.")
        @Schema(description = "장소 이름", example = "카페")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Schema(description = "장소 주소", example = "서울특별시 중구 세종대로 110")
        String address,

        @NotBlank(message = "좌표 토큰은 필수입니다.")
        @Schema(description = "좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken
) {
}

