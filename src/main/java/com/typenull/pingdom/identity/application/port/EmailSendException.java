package com.typenull.pingdom.identity.application.port;

/**
 * 메일 발송 실패의 내부 코드·제공자 코드와 재시도 가능 여부를 상위 처리자에 전달합니다.
 * 이 예외 자체가 재시도를 실행하거나 횟수를 결정하지는 않습니다.
 */
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
