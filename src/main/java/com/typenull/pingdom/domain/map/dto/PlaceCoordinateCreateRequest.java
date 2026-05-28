package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "장소 좌표 생성/확정 요청")
public record PlaceCoordinateCreateRequest(
        @NotNull(message = "기준 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
        @Schema(description = "기준 위도", example = "37.5665")
        Double baseLatitude,

        @NotNull(message = "기준 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
        @Schema(description = "기준 경도", example = "126.9780")
        Double baseLongitude
) {
}

