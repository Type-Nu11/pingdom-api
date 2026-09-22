package com.typenull.pingdom.shared.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private AuthMetrics authMetrics;
    private GlobalExceptionHandler handler;

    /**
     * 인증 메트릭 대역을 주입한 공통 예외 처리기를 만들어 응답 매핑과 관측 호출을 분리 검증.
     */
    @BeforeEach
    void setUp() {
        authMetrics = mock(AuthMetrics.class);
        handler = new GlobalExceptionHandler(authMetrics);
    }

    /**
     * INVALID_TOKEN 도메인 예외의 상태·ErrorResponse와 controller_advice 출처 인증 실패 메트릭을 확인.
     */
    @Test
    @DisplayName("도메인 예외는 공통 ErrorResponse로 변환한다")
    void handlesDomainException() {
        AuthException exception = new AuthException(AuthErrorCode.INVALID_TOKEN);

        ResponseEntity<ErrorResponse> response = handler.handleDomainException(exception);

        assertThat(response.getStatusCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN.getStatus());
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(AuthErrorCode.INVALID_TOKEN));
        verify(authMetrics).recordAuthFailure(AuthErrorCode.INVALID_TOKEN, "controller_advice");
    }

    /**
     * 요청 본문의 name 필드 검증 오류를 VALIDATION_FAILED 코드와 필드별 메시지로 매핑하는지 검증.
     */
    @Test
    @DisplayName("RequestBody Validation 오류는 code와 필드 오류를 함께 반환한다")
    void handlesMethodArgumentValidation() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "이름은 필수입니다."));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ValidationErrorResponse> response = handler.handleValidationException(exception);

        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.getCode());
        assertThat(response.getBody().errors()).containsEntry("name", "이름은 필수입니다.");
    }

    /**
     * ConstraintViolation의 request.id 경로·메시지가 공통 검증 오류 응답에 보존되는지 검증.
     */
    @Test
    @DisplayName("Constraint Validation 오류도 동일한 응답 형식을 사용한다")
    void handlesConstraintValidation() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("request.id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("ID는 양수여야 합니다.");

        ResponseEntity<ValidationErrorResponse> response = handler.handleConstraintViolationException(
                new ConstraintViolationException(Set.of(violation))
        );

        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.getCode());
        assertThat(response.getBody().errors()).containsEntry("request.id", "ID는 양수여야 합니다.");
    }

    /**
     * 예상하지 못한 예외는 INTERNAL_SERVER_ERROR로 응답하고 내부 secret 문자열을 메시지에 노출하지 않는지 검증.
     */
    @Test
    @DisplayName("예상하지 못한 예외는 내부 메시지를 노출하지 않는다")
    void hidesUnexpectedExceptionMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("password=secret")
        );

        assertThat(response.getStatusCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus());
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
