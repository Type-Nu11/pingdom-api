package com.typenull.pingdom.moderation.api.dto.place.quality;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "관리자 장소 관광 정보 전체 교체 요청")
public record AdminMapPlaceTouristInfoUpdateRequest(
        @Size(max = 150, message = "영문 장소명은 150자 이하여야 합니다.")
        @Schema(
                description = "영문 장소명. 공백 또는 null이면 제거합니다.",
                example = "Jinju Castle",
                nullable = true
        )
        String englishName,

        @Size(max = 500, message = "관광객용 요약은 500자 이하여야 합니다.")
        @Schema(description = "관광객용 장소 요약. 공백 또는 null이면 제거합니다.", nullable = true)
        String touristSummary,

        @Size(max = 9, message = "관광 카테고리는 최대 9개까지 선택할 수 있습니다.")
        @Schema(description = "관광 목적 카테고리 전체 목록. 생략하면 모두 제거합니다.")
        Set<@NotNull(message = "관광 카테고리에는 null 값을 포함할 수 없습니다.") TouristCategory> touristCategories,

        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        @Schema(description = "감사 로그에 기록할 수정 사유", example = "영문 정보와 관광 분류 최신화")
        String reason
) {
}
