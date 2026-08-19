package com.typenull.pingdom.boost.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VerifiedBoostErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Verified Boost 상품을 찾을 수 없습니다."),
    PRODUCT_NOT_ACTIVE(HttpStatus.CONFLICT, "활성화된 Verified Boost 상품이 아닙니다."),
    PLACE_NOT_OWNED(HttpStatus.FORBIDDEN, "소유한 장소의 Verified Boost 상품만 관리할 수 있습니다."),
    INVALID_PRODUCT_INPUT(HttpStatus.BAD_REQUEST, "Verified Boost 상품 입력값이 올바르지 않습니다."),
    INVALID_PRODUCT_STATE(HttpStatus.CONFLICT, "현재 Verified Boost 상품 상태에서는 요청을 처리할 수 없습니다."),
    SELECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Verified Boost 선택 내역을 찾을 수 없습니다."),
    EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Verified Boost 집행 내역을 찾을 수 없습니다."),
    EXECUTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "해당 장소에 이미 활성 Boost 집행이 있습니다."),
    INVALID_EXECUTION_STATE(HttpStatus.CONFLICT, "현재 Verified Boost 집행 상태에서는 요청을 처리할 수 없습니다."),
    QUALITY_GUARDRAIL_BLOCKED(HttpStatus.CONFLICT, "운영 품질 기준을 충족한 장소만 Boost를 집행할 수 있습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "다른 요청에 사용된 idempotency key입니다.");

    private final HttpStatus status;
    private final String message;
}
