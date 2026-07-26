package com.typenull.pingdom.payment.application.provider;

public interface PaymentProvider {
    String name();

    /** 같은 command.idempotencyKey에 대해 Provider가 중복 승인하지 않아야 한다. */
    PaymentProviderResult authorize(PaymentProviderCommand command);

    /** 같은 idempotencyKey에 대해 Provider가 중복 환불하지 않아야 한다. */
    void refund(String providerPaymentId, long amountMinor, String currency, String idempotencyKey);
}
