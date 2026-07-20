package com.typenull.pingdom.availability.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AvailabilityException extends RuntimeException {
    private final AvailabilityErrorCode errorCode;

    public AvailabilityException(AvailabilityErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
