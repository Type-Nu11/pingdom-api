package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 예약 API의 메서드 권한 거부가 공통 예상 외 오류 처리에서 500으로 바뀌지 않도록 한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MerchantReservationController.class)
public class ReservationAccessDeniedAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        ReservationErrorCode code = ReservationErrorCode.RESERVATION_FORBIDDEN;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
    }
}
