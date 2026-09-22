package com.typenull.pingdom.shared.exception;

import org.springframework.http.HttpStatus;

/** HTTP 오류 코드와 원인을 보존하는 도메인 예외의 공통 경계. 상세 예외 메시지와 공개 오류 메시지는 다를 수 있음. */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
