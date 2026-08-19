package com.typenull.pingdom.shared.exception.handler;

import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.DomainException;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CampaignException.class)
    public ResponseEntity<Map<String, String>> handleCampaignException(CampaignException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", String.valueOf(exception.getErrorCode())));
    }

    @ExceptionHandler(AnalysisReportException.class)
    public ResponseEntity<Map<String, String>> handleAnalysisReportException(AnalysisReportException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus())
                .body(Map.of("message", exception.getMessage(), "code", String.valueOf(exception.getErrorCode())));
    }

    private final AuthMetrics authMetrics;

    public GlobalExceptionHandler(AuthMetrics authMetrics) {
        this.authMetrics = authMetrics;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception) {
        if (exception instanceof AuthException authException) {
            authMetrics.recordAuthFailure(authException.getErrorCode(), "controller_advice");
        }

        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.from(exception.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.getStatus())
                .body(ValidationErrorResponse.of(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.getStatus())
                .body(ValidationErrorResponse.of(errors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception
    ) {
        String message = exception.getMessage();
        if (message != null) {
            String normalizedMessage = message.toLowerCase();
            if (normalizedMessage.contains("users_username_key")) {
                return errorResponse(AuthErrorCode.DUPLICATE_USERNAME);
            }
            if (normalizedMessage.contains("uk_oauth_accounts_provider_provider_id")) {
                return errorResponse(AuthErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED);
            }
            if (normalizedMessage.contains("map_bookmark")) {
                return errorResponse(MapErrorCode.BOOKMARK_ALREADY_EXISTS);
            }
            if (normalizedMessage.contains("uk_map_image_user_place")) {
                return errorResponse(MapErrorCode.ALREADY_POSTED);
            }
        }

        return errorResponse(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null
                ? CommonErrorCode.REQUEST_FAILED.getMessage()
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErrorResponse(message, CommonErrorCode.REQUEST_FAILED.getCode()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
        return errorResponse(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        return errorResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception type={}", exception.getClass().getName());
        return errorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> errorResponse(com.typenull.pingdom.shared.exception.ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }
}
