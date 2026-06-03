package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 생성 응답")
public record PlaceCreateResponse(
        @Schema(description = "생성된 장소 ID", example = "1")
        Long id,
        @Schema(description = "장소 이름", example = "카페")
        String name,
        @Schema(description = "주소", example = "서울특별시 ...")
        String address,
        @Schema(description = "위도", example = "37.5665")
        Double latitude,
        @Schema(description = "경도", example = "126.9780")
        Double longitude
) {
}

