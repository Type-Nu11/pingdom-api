package com.typenull.pingdom.verification.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class VisitorVerificationException extends DomainException {

    public VisitorVerificationException(VisitorVerificationErrorCode errorCode) {
        super(errorCode);
    }
}
