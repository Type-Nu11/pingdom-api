package com.typenull.pingdom.place.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "장소 생성 요청")
public record PlaceCreateRequest(
        @NotBlank(message = "장소 이름은 필수입니다.")
        @Schema(description = "장소 이름", example = "카페")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Schema(description = "장소 주소", example = "서울특별시 중구 세종대로 110")
        String address,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
        @Schema(description = "위도", example = "37.5665")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
        @Schema(description = "경도", example = "126.9780")
        Double longitude
) {
}
