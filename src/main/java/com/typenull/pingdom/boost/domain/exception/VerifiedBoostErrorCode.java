package com.typenull.pingdom.boost.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VerifiedBoostErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Verified Boost 상품을 찾을 수 없습니다."),
    PLACE_NOT_OWNED(HttpStatus.FORBIDDEN, "소유한 장소의 Verified Boost 상품만 관리할 수 있습니다."),
    INVALID_PRODUCT_INPUT(HttpStatus.BAD_REQUEST, "Verified Boost 상품 입력값이 올바르지 않습니다."),
    INVALID_PRODUCT_STATE(HttpStatus.CONFLICT, "현재 Verified Boost 상품 상태에서는 요청을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
