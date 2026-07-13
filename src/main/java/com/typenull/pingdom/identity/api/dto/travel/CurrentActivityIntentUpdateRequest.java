package com.typenull.pingdom.identity.api.dto.travel;

import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "현재 행동 의도 변경 요청")
public record CurrentActivityIntentUpdateRequest(
        @NotNull(message = "현재 행동 의도는 필수입니다.")
        @Schema(description = "현재 탐색하려는 행동", example = "CAFE")
        CurrentActivityIntent intent
) {
}
