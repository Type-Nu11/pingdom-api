package com.typenull.pingdom.notification.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NotificationsException extends RuntimeException {

    private final NotificationsErrorCode errorCode;

    public NotificationsException(NotificationsErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public NotificationsException(NotificationsErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
