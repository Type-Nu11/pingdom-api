package com.typenull.pingdom.boost.domain.exception;

import lombok.Getter;

@Getter
public class VerifiedBoostException extends RuntimeException {

    private final VerifiedBoostErrorCode errorCode;

    public VerifiedBoostException(VerifiedBoostErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
