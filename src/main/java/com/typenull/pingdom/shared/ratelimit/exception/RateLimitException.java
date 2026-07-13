package com.typenull.pingdom.shared.ratelimit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RateLimitException extends RuntimeException {

    public static final String CODE = "RATE_LIMIT_EXCEEDED";

    public RateLimitException(String message) {
        super(message);
    }

    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    public String getCode() {
        return CODE;
    }
}
