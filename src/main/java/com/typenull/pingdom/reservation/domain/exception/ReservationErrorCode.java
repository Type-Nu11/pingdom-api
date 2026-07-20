package com.typenull.pingdom.reservation.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode {
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "이 예약을 처리할 권한이 없습니다."),
    INVALID_RESERVATION_INPUT(HttpStatus.BAD_REQUEST, "예약 입력값이 올바르지 않습니다."),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "현재 예약 상태에서는 요청을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "동일한 멱등성 키가 다른 예약 요청에 사용되었습니다."),
    TOURIST_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "일반 사용자 계정만 예약할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
