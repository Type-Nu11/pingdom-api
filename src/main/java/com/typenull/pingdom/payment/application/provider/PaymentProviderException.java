package com.typenull.pingdom.payment.application.provider;

import lombok.Getter;

@Getter
public class PaymentProviderException extends RuntimeException {
    private final String failureCode;

    public PaymentProviderException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }
}
