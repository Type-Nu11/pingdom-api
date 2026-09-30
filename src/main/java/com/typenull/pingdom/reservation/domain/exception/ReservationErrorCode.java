package com.typenull.pingdom.reservation.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {
    QUOTE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "예약 견적 발급 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 견적을 찾을 수 없습니다."),
    QUOTE_TERMS_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "예약 가격 또는 취소 조건이 제공되지 않았습니다."),
    QUOTE_EXPIRED(HttpStatus.GONE, "예약 견적이 만료되어 재확인이 필요합니다."),
    QUOTE_CONDITIONS_CHANGED(HttpStatus.CONFLICT, "예약 조건이 변경되어 재확인이 필요합니다."),
    QUOTE_REQUEST_MISMATCH(HttpStatus.CONFLICT, "확인한 예약 대상 또는 인원과 요청이 일치하지 않습니다."),
    RESERVATION_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 슬롯이 사라져 재확인이 필요합니다."),
    RESERVATION_SLOT_INACTIVE(HttpStatus.CONFLICT, "예약 슬롯이 비활성 또는 시작되어 재확인이 필요합니다."),
    RESERVATION_PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT, "예약 상품 연결이 유효하지 않아 재확인이 필요합니다."),
    RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 정원이 부족하여 재확인이 필요합니다."),
    CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "수락한 취소 정책에 따라 취소할 수 없습니다."),
    RESERVATION_REFUND_REQUIRED(HttpStatus.CONFLICT, "결제 처리 결과 및 환불 완료를 확인한 뒤 취소해야 합니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "이 예약을 처리할 권한이 없습니다."),
    INVALID_RESERVATION_INPUT(HttpStatus.BAD_REQUEST, "예약 입력값이 올바르지 않습니다."),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "현재 예약 상태에서는 요청을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "동일한 멱등성 키가 다른 예약 요청에 사용되었습니다."),
    TOURIST_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "일반 사용자 계정만 예약할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
