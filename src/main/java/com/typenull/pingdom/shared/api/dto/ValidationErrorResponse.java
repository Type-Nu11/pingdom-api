package com.typenull.pingdom.shared.api.dto;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "필드 검증 오류 응답")
public record ValidationErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(example = "VALIDATION_FAILED", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> errors
) {
    public static ValidationErrorResponse of(Map<String, String> errors) {
        return new ValidationErrorResponse(
                CommonErrorCode.VALIDATION_FAILED.getMessage(),
                CommonErrorCode.VALIDATION_FAILED.getCode(),
                Map.copyOf(errors)
        );
    }
}
