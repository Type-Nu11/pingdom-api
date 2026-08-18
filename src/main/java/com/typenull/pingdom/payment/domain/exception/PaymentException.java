package com.typenull.pingdom.payment.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class PaymentException extends DomainException {

    public PaymentException(PaymentErrorCode errorCode) {
        super(errorCode);
    }
}
