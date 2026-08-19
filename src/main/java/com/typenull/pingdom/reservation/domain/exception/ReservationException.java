package com.typenull.pingdom.reservation.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class ReservationException extends DomainException {

    public ReservationException(ReservationErrorCode errorCode) {
        super(errorCode);
    }
}
