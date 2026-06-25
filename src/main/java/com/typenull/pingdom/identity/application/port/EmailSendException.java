package com.typenull.pingdom.identity.application.port;

public class EmailSendException extends RuntimeException {

    private final String errorCode;
    private final String providerErrorCode;
    private final boolean retryable;

    public EmailSendException(
            String message,
            String errorCode,
            String providerErrorCode,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerErrorCode = providerErrorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getProviderErrorCode() {
        return providerErrorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
