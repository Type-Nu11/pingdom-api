package com.typenull.pingdom.shared.ratelimit.exception;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.exception.DomainException;

public class RateLimitException extends DomainException {

    public RateLimitException(String message) {
        super(CommonErrorCode.RATE_LIMIT_EXCEEDED, message);
    }

    public String getCode() {
        return getErrorCode().getCode();
    }
}
