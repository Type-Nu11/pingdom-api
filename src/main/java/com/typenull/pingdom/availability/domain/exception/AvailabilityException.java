package com.typenull.pingdom.availability.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class AvailabilityException extends DomainException {

    public AvailabilityException(AvailabilityErrorCode errorCode) {
        super(errorCode);
    }
}
