package com.typenull.pingdom.moderation.api.dto.place.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 이벤트 상태 변경 요청")
public record AdminPlaceEventActionRequest(
        @Schema(description = "변경 사유", example = "운영 검토를 완료했습니다.")
        @NotBlank(message = "변경 사유는 필수입니다.")
        @Size(max = 500, message = "변경 사유는 500자 이하여야 합니다.")
        String reason
) {
}
