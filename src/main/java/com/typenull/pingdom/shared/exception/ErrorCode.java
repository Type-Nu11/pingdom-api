package com.typenull.pingdom.shared.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    default String getCode() {
        return ((Enum<?>) this).name();
    }
}
