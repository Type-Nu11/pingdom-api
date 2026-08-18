package com.typenull.pingdom.moderation.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class AdminException extends DomainException {

    public AdminException(AdminErrorCode errorCode) {
        super(errorCode);
    }

    public AdminException(AdminErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
