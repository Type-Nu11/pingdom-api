package com.typenull.pingdom.identity.api.dto.profile;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "여행 목적 선호 전체 변경 요청")
public record TravelPurposePreferenceUpdateRequest(
        @NotNull(message = "여행 목적 선호 목록은 필수입니다.")
        @Size(max = 9, message = "여행 목적은 최대 9개까지 선택할 수 있습니다.")
        @Schema(
                description = "선호 여행 목적 전체 목록. 빈 배열이면 모든 선호를 해제합니다.",
                example = "[\"K_POP\", \"FOOD\"]"
        )
        Set<@NotNull(message = "여행 목적에는 null을 포함할 수 없습니다.") TravelPurpose> travelPurposes
) {
}
