package com.typenull.pingdom.shared.ratelimit;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RateLimitUnavailableException extends RuntimeException {

    public static final String CODE = "RATE_LIMIT_UNAVAILABLE";
    private static final String MESSAGE = "요청 처리에 필요한 제한 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.";

    public RateLimitUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }

    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    public String getCode() {
        return CODE;
    }
}
