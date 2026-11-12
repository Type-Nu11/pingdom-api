package com.typenull.pingdom.shared.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Test
    void mapsMultipartSizeFailureToPayloadTooLarge() {
        ResponseEntity<ErrorResponse> response = handler.handleUploadTooLarge(new org.springframework.web.multipart.MaxUploadSizeExceededException(10));
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().code()).isEqualTo("UPLOAD_TOO_LARGE");
    }

    @Test
    void mapsMissingMultipartFileToBadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidRequestParameter(
                new org.springframework.web.multipart.support.MissingServletRequestPartException("file"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST_PARAMETER");
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

    @Test
    void mapsUserOptimisticConflictToConflictWithoutExposingInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException(User.class, 1L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(UsersErrorCode.USER_CONCURRENT_MODIFICATION));
        assertThat(response.getBody().message()).doesNotContain(User.class.getName());
    }

    @Test
    void mapsUserJpaFlushConflictToSamePublicContract() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(
                new OptimisticLockException("internal password=secret", null, User.builder().id(1L).build()));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(UsersErrorCode.USER_CONCURRENT_MODIFICATION));
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void mapsJpaTranslatedUserConflictWithoutClassMetadata() {
        JpaOptimisticLockingFailureException exception = new JpaOptimisticLockingFailureException(
                new OptimisticLockException("stale User", null, User.builder().id(1L).build()));
        assertThat(exception.getPersistentClass()).isNull();

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(UsersErrorCode.USER_CONCURRENT_MODIFICATION));
    }

    @Test
    void mapsWrappedUserCommitConflict() {
        TransactionSystemException exception = new TransactionSystemException("commit failed",
                new jakarta.persistence.RollbackException(new OptimisticLockException(
                        "stale User", null, User.builder().id(1L).build())));

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(UsersErrorCode.USER_CONCURRENT_MODIFICATION));
    }

    @Test
    void returnsConflictForWrappedUserCommitFailureThroughMvcResolver() throws Exception {
        MockMvcBuilders.standaloneSetup(new WrappedConflictController())
                .setControllerAdvice(handler)
                .build()
                .perform(get("/test/user-commit-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_CONCURRENT_MODIFICATION"));
    }

    @Test
    void preservesInternalErrorForAnotherEntityCommitFailureThroughMvcResolver() throws Exception {
        MockMvcBuilders.standaloneSetup(new WrappedConflictController())
                .setControllerAdvice(handler)
                .build()
                .perform(get("/test/other-commit-conflict"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void recognizesUserProxyClassMetadata() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException(UserProxy.class, 1L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(UsersErrorCode.USER_CONCURRENT_MODIFICATION));
    }

    @Test
    void doesNotReclassifyAnotherEntityConflictAsUserConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException(TravelSchedule.class, 1L));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    void preservesExistingTravelScheduleConflictContract() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new UsersException(UsersErrorCode.TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody())
                .isEqualTo(ErrorResponse.from(UsersErrorCode.TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION));
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

    private static class UserProxy extends User {
    }

    @RestController
    static class WrappedConflictController {

        @GetMapping("/test/user-commit-conflict")
        public void userCommitFailure() {
            throw new TransactionSystemException("commit failed",
                    new jakarta.persistence.RollbackException(new OptimisticLockException(
                            "stale User", null, User.builder().id(1L).build())));
        }

        @GetMapping("/test/other-commit-conflict")
        public void otherCommitFailure() {
            throw new TransactionSystemException("commit failed",
                    new jakarta.persistence.RollbackException(
                            new ObjectOptimisticLockingFailureException(TravelSchedule.class, 1L)));
        }
    }
}
