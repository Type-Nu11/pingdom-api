package com.typenull.pingdom.boost.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class VerifiedBoostException extends DomainException {

    public VerifiedBoostException(VerifiedBoostErrorCode errorCode) {
        super(errorCode);
    }
}
