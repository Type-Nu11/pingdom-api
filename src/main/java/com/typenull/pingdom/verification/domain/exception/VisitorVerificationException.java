package com.typenull.pingdom.verification.domain.exception;

import lombok.Getter;

@Getter
public class VisitorVerificationException extends RuntimeException {
    private final VisitorVerificationErrorCode errorCode;

    public VisitorVerificationException(VisitorVerificationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
