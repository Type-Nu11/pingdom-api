package com.typenull.pingdom.shared.api.dto;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import java.util.Map;

public record ValidationErrorResponse(
        String message,
        String code,
        Map<String, String> errors
) {
    public static ValidationErrorResponse of(Map<String, String> errors) {
        return new ValidationErrorResponse(
                CommonErrorCode.VALIDATION_FAILED.getMessage(),
                CommonErrorCode.VALIDATION_FAILED.getCode(),
                Map.copyOf(errors)
        );
    }
}
