package com.typenull.pingdom.offer.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OfferErrorCode {
    OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Offer를 찾을 수 없습니다."),
    PLACE_NOT_OWNED(HttpStatus.FORBIDDEN, "소유한 장소의 Offer만 관리할 수 있습니다."),
    INVALID_OFFER_PERIOD(HttpStatus.BAD_REQUEST, "Offer 기간이 올바르지 않습니다."),
    INVALID_OFFER_INPUT(HttpStatus.BAD_REQUEST, "Offer 입력값이 올바르지 않습니다."),
    INVALID_OFFER_STATE(HttpStatus.CONFLICT, "현재 Offer 상태에서는 요청을 처리할 수 없습니다."),
    OFFER_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 발급 가능한 Offer가 아닙니다."),
    OFFER_SOLD_OUT(HttpStatus.CONFLICT, "Offer의 쿠폰이 모두 발급되었습니다."),
    TOURIST_ELIGIBILITY_REQUIRED(HttpStatus.FORBIDDEN, "진행 중인 여행 일정이 있는 일반 사용자만 쿠폰을 발급할 수 있습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 Offer 쿠폰입니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    COUPON_NOT_REDEEMABLE(HttpStatus.CONFLICT, "현재 사용할 수 없는 쿠폰입니다.");

    private final HttpStatus status;
    private final String message;
}
