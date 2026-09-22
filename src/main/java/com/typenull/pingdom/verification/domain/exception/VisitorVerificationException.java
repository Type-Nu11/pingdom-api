package com.typenull.pingdom.verification.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

/** 방문 인증 계열 오류 코드를 공통 DomainException 처리 경로에 전달한다. */
public class VisitorVerificationException extends DomainException {

    public VisitorVerificationException(VisitorVerificationErrorCode errorCode) {
        super(errorCode);
    }
}
