package com.typenull.pingdom.shared.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 메시지", example = "유효하지 않은 토큰입니다.")
        String message,
        @Schema(description = "에러 코드", example = "INVALID_TOKEN")
        String code
) {
}
