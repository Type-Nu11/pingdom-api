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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/** 컨트롤러 예외를 도메인·검증·공통 오류 응답으로 변환한다. Security 필터 오류는 별도 보안 응답기가 담당한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CampaignException.class)
    public ResponseEntity<Map<String, String>> handleCampaignException(CampaignException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", String.valueOf(exception.getErrorCode())));
    }

    @ExceptionHandler(AnalysisReportException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisReportException(AnalysisReportException exception) {
        // PDF 응답 요청이라도 분석 실패는 항상 JSON 오류 계약으로 반환한다.
        // Content-Type을 명시하지 않으면 Accept: application/pdf와 협상 충돌해 502가 500으로 변질될 수 있다.
        return ResponseEntity.status(exception.getErrorCode().getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(exception.getErrorCode()));
    }

    private final AuthMetrics authMetrics;

    public GlobalExceptionHandler(AuthMetrics authMetrics) {
        this.authMetrics = authMetrics;
    }

    /** 도메인 코드의 상태·공개 메시지를 반환한다. 인증 예외는 advice 발생 위치의 실패 메트릭도 기록한다. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception) {
        if (exception instanceof AuthException authException) {
            authMetrics.recordAuthFailure(authException.getErrorCode(), "controller_advice");
        }

        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.from(exception.getErrorCode()));
    }

    /** 같은 필드의 여러 Bean Validation 오류 중 먼저 만난 메시지만 유지한다. 필드 오류만 응답에 포함한다. */
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return errorResponse(CommonErrorCode.INVALID_REQUEST_BODY);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(Exception exception) {
        return errorResponse(CommonErrorCode.INVALID_REQUEST_PARAMETER);
    }

    /** 제약 위반의 property path를 키로 사용하고 같은 경로는 처음 수집한 메시지를 유지한다. */
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

    /** 예외 메시지의 알려진 제약명·테이블 문자열로 중복 오류를 분류한다. 매칭되지 않은 무결성 실패는 공통 500으로 반환한다. */
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

    /** 요청한 HTTP 상태와 reason을 유지한다. reason이 null일 때만 공통 요청 실패 메시지로 대체한다. */
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

    /** 처리되지 않은 예외의 클래스명만 로그에 남기고 내부 상세를 제외한 공통 500 응답을 반환한다. */
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
