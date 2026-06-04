package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 상세 조회 응답")
public record PlaceDetailResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String registrant
) {
}
