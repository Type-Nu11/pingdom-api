package com.typenull.pingdom.identity.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class UsersException extends DomainException {

    public UsersException(UsersErrorCode errorCode) {
        super(errorCode);
    }
}
