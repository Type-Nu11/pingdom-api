package com.typenull.pingdom.identity.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class AuthException extends DomainException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public AuthErrorCode getErrorCode() {
        return (AuthErrorCode) super.getErrorCode();
    }
}
