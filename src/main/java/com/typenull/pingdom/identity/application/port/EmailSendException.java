package com.typenull.pingdom.identity.application.port;

/**
 * 메일 발송 실패의 내부 코드·제공자 코드와 재시도 가능 여부를 상위 처리자에 전달.
 * 재시도 실행과 횟수 결정은 이 예외 타입의 책임 범위에서 제외.
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
