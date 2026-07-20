package com.typenull.pingdom.reservation.domain.exception;

import lombok.Getter;

@Getter
public class ReservationException extends RuntimeException {
    private final ReservationErrorCode errorCode;

    public ReservationException(ReservationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
