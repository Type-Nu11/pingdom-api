package com.typenull.pingdom.shared.ratelimit.exception;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.DomainException;

public class RateLimitUnavailableException extends DomainException {

    public RateLimitUnavailableException(Throwable cause) {
        super(CommonErrorCode.RATE_LIMIT_UNAVAILABLE, cause);
    }

    public String getCode() {
        return getErrorCode().getCode();
    }
}
