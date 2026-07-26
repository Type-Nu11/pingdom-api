package com.typenull.pingdom.payment.application.provider;

import lombok.Getter;

@Getter
public class PaymentProviderException extends RuntimeException {
    private final String failureCode;
    private final PaymentProviderFailureType failureType;

    public PaymentProviderException(PaymentProviderFailureType failureType, String failureCode, String message) {
        super(message);
        this.failureType = failureType;
        this.failureCode = failureCode;
    }
}
