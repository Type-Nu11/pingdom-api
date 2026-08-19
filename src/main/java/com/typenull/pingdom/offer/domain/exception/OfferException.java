package com.typenull.pingdom.offer.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class OfferException extends DomainException {

    public OfferException(OfferErrorCode errorCode) {
        super(errorCode);
    }
}
