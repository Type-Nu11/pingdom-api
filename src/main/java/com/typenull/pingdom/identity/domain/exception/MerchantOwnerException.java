package com.typenull.pingdom.identity.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class MerchantOwnerException extends DomainException {

    public MerchantOwnerException(MerchantOwnerErrorCode errorCode) {
        super(errorCode);
    }
}
