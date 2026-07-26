package com.typenull.pingdom.payment.application.provider;

public interface PaymentProvider {
    String name();

    PaymentProviderResult authorize(PaymentProviderCommand command);

    void refund(String providerPaymentId, long amountMinor, String currency, String idempotencyKey);
}
