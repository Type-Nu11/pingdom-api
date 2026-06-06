package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 업로드 요청(좌표 토큰 기반)")
public record PlaceUploadRequest(
        @NotBlank(message = "카카오 장소 ID는 필수입니다.")
        @Size(max = 50, message = "카카오 장소 ID는 50자 이하여야 합니다.")
        @Schema(description = "카카오 장소 ID", example = "27414316")
        String kakaoPlaceId,

        @NotBlank(message = "장소 이름은 필수입니다.")
        @Size(max = 100, message = "장소 이름은 100자 이하여야 합니다.")
        @Schema(description = "장소 이름", example = "카페")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        @Schema(description = "장소 주소", example = "서울특별시 중구 세종대로 110")
        String address,

        @NotBlank(message = "이미지 URL은 필수입니다.")
        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Schema(description = "장소 대표 이미지 URL", example = "https://example.com/images/place-1.jpg")
        String imageUrl,

        @NotBlank(message = "좌표 토큰은 필수입니다.")
        @Schema(description = "좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken
) {
}
