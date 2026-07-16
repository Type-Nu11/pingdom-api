package com.typenull.pingdom.offer.domain.exception;

import lombok.Getter;

@Getter
public class OfferException extends RuntimeException {

    private final OfferErrorCode errorCode;

    public OfferException(OfferErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
