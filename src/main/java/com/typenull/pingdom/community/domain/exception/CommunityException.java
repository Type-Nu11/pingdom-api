package com.typenull.pingdom.community.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class CommunityException extends DomainException {

    public CommunityException(CommunityErrorCode errorCode) {
        super(errorCode);
    }
}
