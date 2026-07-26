package com.typenull.pingdom.payment.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 거래를 찾을 수 없습니다."),
    PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "이 결제 거래를 처리할 권한이 없습니다."),
    INVALID_PAYMENT_INPUT(HttpStatus.BAD_REQUEST, "결제 입력값이 올바르지 않습니다."),
    INVALID_PAYMENT_STATE(HttpStatus.CONFLICT, "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "동일한 멱등성 키가 다른 결제 요청에 사용되었습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "예약에 처리 중이거나 완료된 결제가 이미 존재합니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 결제 Provider입니다."),
    PROVIDER_REJECTED(HttpStatus.BAD_GATEWAY, "결제 Provider가 요청을 처리하지 못했습니다."),
    RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "현재 예약은 결제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
