package com.typenull.pingdom.moderation.api.dto.place.quality.basic;

import com.typenull.pingdom.place.domain.place.category.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 기본 정보 수정 요청")
public record AdminMapPlaceBasicInformationUpdateRequest(
        @NotBlank(message = "장소명은 필수입니다.")
        @Size(max = 100, message = "장소명은 100자 이하여야 합니다.")
        @Schema(description = "수정할 장소명", example = "진주성")
        String name,

        @NotNull(message = "장소 카테고리는 필수입니다.")
        @Schema(description = "수정할 표준 장소 카테고리", example = "CULTURAL_HERITAGE")
        PlaceCategory category,

        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        @Schema(description = "감사 로그에 기록할 수정 사유", example = "장소명과 분류 오등록 정정")
        String reason
) {
}
