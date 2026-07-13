package com.typenull.pingdom.moderation.api.dto.place.quality.geocoding;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminMapPlaceGeocodingUpdateRequest(
        @NotBlank(message = "대표 주소는 필수입니다.")
        @Size(max = 255, message = "대표 주소는 255자 이하여야 합니다.")
        String address,

        @Size(max = 255, message = "도로명 주소는 255자 이하여야 합니다.")
        String roadAddress,

        @Size(max = 255, message = "지번 주소는 255자 이하여야 합니다.")
        String jibunAddress,

        @Size(max = 20, message = "우편번호는 20자 이하여야 합니다.")
        String postalCode,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
        Double longitude,

        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        String reason
) {
}
