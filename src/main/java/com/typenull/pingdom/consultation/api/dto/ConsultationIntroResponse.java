package com.typenull.pingdom.consultation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "첫 상담 안내 응답")
public record ConsultationIntroResponse(
        @Schema(description = "사용자에게 표시할 첫 상담 안내문", example = "카페 창업을 고민하고 계시는군요. 먼저 관심 있는 업종을 선택해 주세요.")
        String message,

        @Schema(description = "안내문 생성 출처", allowableValues = {"gemini", "fallback"}, example = "gemini")
        String source
) {
}
