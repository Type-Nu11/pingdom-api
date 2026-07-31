package com.typenull.pingdom.payment.application.provider;

public record PaymentProviderResult(
        String providerPaymentId,
        long amountMinor,
        long feeAmountMinor,
        String currency
) {}
