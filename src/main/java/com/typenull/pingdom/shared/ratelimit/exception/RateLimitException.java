package com.typenull.pingdom.shared.ratelimit.exception;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.DomainException;

/** 저장소가 허용 횟수 또는 재요청 간격 초과를 확인했을 때 반환하는 도메인 오류. */
public class RateLimitException extends DomainException {

    public RateLimitException(String message) {
        super(CommonErrorCode.RATE_LIMIT_EXCEEDED, message);
    }

    public String getCode() {
        return getErrorCode().getCode();
    }
}
