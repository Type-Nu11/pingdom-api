package com.typenull.pingdom.global.exception.handler;

import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<Map<String, String>> handleAdminException(AdminException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
    }

    @ExceptionHandler(MapException.class)
    public ResponseEntity<Map<String, String>> handleMapException(MapException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage(), "code", exception.getErrorCode().name()));
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
            if (message.contains("users_username_key")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", AuthErrorCode.DUPLICATE_USERNAME.getMessage(), "code", AuthErrorCode.DUPLICATE_USERNAME.name()));
            }
            if (message.contains("map_favorite")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", MapErrorCode.FAVORITE_ALREADY_EXISTS.getMessage(), "code", MapErrorCode.FAVORITE_ALREADY_EXISTS.name()));
            }
            if (message.contains("uk_map_place_kakao_place_id")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "message", AdminErrorCode.KAKAO_PLACE_ID_ALREADY_CONNECTED.getMessage(),
                                "code", AdminErrorCode.KAKAO_PLACE_ID_ALREADY_CONNECTED.name()
                        ));
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "데이터 무결성 오류가 발생했습니다."));
    }
}
