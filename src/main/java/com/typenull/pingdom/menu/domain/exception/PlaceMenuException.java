package com.typenull.pingdom.menu.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class PlaceMenuException extends DomainException {
    public PlaceMenuException(PlaceMenuErrorCode errorCode) {
        super(errorCode);
    }
}
