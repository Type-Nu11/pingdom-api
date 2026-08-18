package com.typenull.pingdom.place.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class PlaceRegistrationException extends DomainException {

    public PlaceRegistrationException(PlaceRegistrationErrorCode errorCode) {
        super(errorCode);
    }
}
