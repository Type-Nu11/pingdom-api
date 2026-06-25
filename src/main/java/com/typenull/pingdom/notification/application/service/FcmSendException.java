package com.typenull.pingdom.notification.application.service;

public class FcmSendException extends RuntimeException {

    private final boolean invalidToken;
    private final String providerErrorCode;

    public FcmSendException(String message, boolean invalidToken, Throwable cause) {
        this(message, invalidToken, null, cause);
    }

    public FcmSendException(String message, boolean invalidToken, String providerErrorCode, Throwable cause) {
        super(message, cause);
        this.invalidToken = invalidToken;
        this.providerErrorCode = providerErrorCode;
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }

    public String getProviderErrorCode() {
        return providerErrorCode;
    }
}
