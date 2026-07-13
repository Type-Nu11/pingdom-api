package com.typenull.pingdom.identity.domain.exception;

import lombok.Getter;

@Getter
public class MerchantOwnerException extends RuntimeException {

    private final MerchantOwnerErrorCode errorCode;

    public MerchantOwnerException(MerchantOwnerErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
