package com.typenull.pingdom.notification.application.service;

public class FcmSendException extends RuntimeException {

    private final boolean invalidToken;

    public FcmSendException(String message, boolean invalidToken, Throwable cause) {
        super(message, cause);
        this.invalidToken = invalidToken;
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }
}
