package com.typenull.pingdom.consultation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "첫 상담 안내 요청")
public record ConsultationIntroRequest(
        @NotBlank(message = "첫 상담 질문은 필수입니다.")
        @Size(max = 300, message = "첫 상담 질문은 300자 이하여야 합니다.")
        @Schema(description = "사용자가 입력한 첫 상담 질문", example = "작은 카페를 열고 싶은데 어떤 업종으로 시작하면 좋을까요?")
        String message
) {
}
