package com.typenull.pingdom.shared.ratelimit.exception;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.DomainException;

/** 제한 저장소 장애로 허용 여부를 판단하지 못했고 fail-open이 꺼진 경우 원인을 보존해 전달. */
public class RateLimitUnavailableException extends DomainException {

    public RateLimitUnavailableException(Throwable cause) {
        super(CommonErrorCode.RATE_LIMIT_UNAVAILABLE, cause);
    }

    public String getCode() {
        return getErrorCode().getCode();
    }
}
