package com.typenull.pingdom.notification.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class NotificationsException extends DomainException {

    public NotificationsException(NotificationsErrorCode errorCode) {
        super(errorCode);
    }

    public NotificationsException(NotificationsErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
