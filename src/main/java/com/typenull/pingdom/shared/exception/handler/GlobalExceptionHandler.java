package com.typenull.pingdom.shared.exception.handler;

import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.reservation.domain.exception.ReservationException;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CampaignException.class)
    public ResponseEntity<Map<String, String>> handleCampaignException(CampaignException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    private final AuthMetrics authMetrics;

    public GlobalExceptionHandler(AuthMetrics authMetrics) {
        this.authMetrics = authMetrics;
    }

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<Map<String, String>> handleAdminException(AdminException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException exception) {
        authMetrics.recordAuthFailure(exception.getErrorCode(), "controller_advice");
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(UsersException.class)
    public ResponseEntity<Map<String, String>> handleUsersException(UsersException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(MerchantOwnerException.class)
    public ResponseEntity<Map<String, String>> handleMerchantOwnerException(MerchantOwnerException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(OfferException.class)
    public ResponseEntity<Map<String, String>> handleOfferException(OfferException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(VerifiedBoostException.class)
    public ResponseEntity<Map<String, String>> handleVerifiedBoostException(VerifiedBoostException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(AvailabilityException.class)
    public ResponseEntity<Map<String, String>> handleAvailabilityException(AvailabilityException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<Map<String, String>> handleReservationException(ReservationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(VisitorVerificationException.class)
    public ResponseEntity<Map<String, String>> handleVisitorVerificationException(
            VisitorVerificationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(MapException.class)
    public ResponseEntity<Map<String, String>> handleMapException(MapException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(NotificationsException.class)
    public ResponseEntity<Map<String, String>> handleNotificationsException(NotificationsException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitException(RateLimitException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getCode()));
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitUnavailableException(
            RateLimitUnavailableException exception
    ) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", "입력값을 확인해주세요.",
                        "errors", errors
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        String message = exception.getMessage();
        if (message != null) {
            String normalizedMessage = message.toLowerCase();
            if (normalizedMessage.contains("users_username_key")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", AuthErrorCode.DUPLICATE_USERNAME.getMessage(), "code", AuthErrorCode.DUPLICATE_USERNAME.name()));
            }
            if (normalizedMessage.contains("uk_oauth_accounts_provider_provider_id")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", AuthErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED.getMessage(), "code", AuthErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED.name()));
            }
            if (normalizedMessage.contains("map_bookmark")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", MapErrorCode.BOOKMARK_ALREADY_EXISTS.getMessage(), "code", MapErrorCode.BOOKMARK_ALREADY_EXISTS.name()));
            }
            if (normalizedMessage.contains("uk_map_image_user_place")) {
                return ResponseEntity.status(MapErrorCode.ALREADY_POSTED.getStatus())
                        .body(Map.of("message", MapErrorCode.ALREADY_POSTED.getMessage(), "code", MapErrorCode.ALREADY_POSTED.name()));
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "데이터 무결성 오류가 발생했습니다."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "요청 처리 중 오류가 발생했습니다." : exception.getReason()));
    }
}
