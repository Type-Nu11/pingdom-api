package com.typenull.pingdom.place.domain.exception;

import lombok.Getter;

@Getter
public class PlaceRegistrationException extends RuntimeException {
    private final PlaceRegistrationErrorCode errorCode;
    public PlaceRegistrationException(PlaceRegistrationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
