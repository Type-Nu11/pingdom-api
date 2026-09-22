package com.typenull.pingdom.shared.api.dto;

import com.typenull.pingdom.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 클라이언트에 노출할 메시지와 오류 코드를 담음. from은 예외 상세 메시지 대신 ErrorCode의 공개 메시지를 사용. */
@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(
                description = "에러 메시지",
                example = "유효하지 않은 토큰입니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message,
        @Schema(
                description = "도메인 에러 코드. 일부 공통 검증 오류에서는 제공되지 않을 수 있습니다.",
                example = "INVALID_TOKEN",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String code
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getMessage(), errorCode.getCode());
    }
}
