package com.typenull.pingdom.place.api.dto.place;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "장소 업로드 요청(좌표 토큰 기반)")
public record PlaceUploadRequest(
        @Size(max = 50, message = "카카오 장소 ID는 50자 이하여야 합니다.")
        @Schema(description = "카카오 장소 ID(선택)", example = "27414316")
        String kakaoPlaceId,

        @NotBlank(message = "장소 이름은 필수입니다.")
        @Size(max = 100, message = "장소 이름은 100자 이하여야 합니다.")
        @Schema(description = "장소 이름", example = "카페")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        @Schema(description = "장소 주소", example = "서울특별시 중구 세종대로 110")
        String address,

        @Size(max = 255, message = "도로명 주소는 255자 이하여야 합니다.")
        @Schema(description = "도로명 주소(선택)", nullable = true)
        String roadAddress,

        @Size(max = 255, message = "지번 주소는 255자 이하여야 합니다.")
        @Schema(description = "지번 주소(선택)", nullable = true)
        String jibunAddress,

        @Size(max = 20, message = "우편번호는 20자 이하여야 합니다.")
        @Schema(description = "우편번호(선택)", nullable = true)
        String postalCode,

        @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
        @Schema(description = "장소 카테고리", example = "카페")
        String category,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Schema(description = "장소 대표 이미지 URL(선택)", example = "https://example.com/images/place-1.jpg")
        String imageUrl,

        @Size(max = 150, message = "영문 장소 이름은 150자 이하여야 합니다.")
        @Schema(description = "영문 장소 이름(선택)", example = "Jinju Castle", nullable = true)
        String englishName,

        @Size(max = 500, message = "관광객용 요약은 500자 이하여야 합니다.")
        @Schema(
                description = "관광객용 장소 요약(선택)",
                example = "남강을 따라 조성된 진주의 대표 역사 관광지입니다.",
                nullable = true
        )
        String touristSummary,

        @Size(max = 9, message = "관광 카테고리는 최대 9개까지 선택할 수 있습니다.")
        @Schema(description = "관광 목적 카테고리(선택)")
        Set<@NotNull(message = "관광 카테고리에는 null을 포함할 수 없습니다.") TouristCategory> touristCategories,

        @NotBlank(message = "좌표 토큰은 필수입니다.")
        @Schema(description = "좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken
) {
}
