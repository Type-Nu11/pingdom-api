package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.payment.api.dto.PaymentCreateRequest;
import com.typenull.pingdom.payment.api.dto.PaymentResponse;
import com.typenull.pingdom.payment.application.provider.*;
import com.typenull.pingdom.payment.domain.exception.PaymentErrorCode;
import com.typenull.pingdom.payment.domain.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentLedgerWriter ledgerWriter;

    public PaymentResponse create(Long userId, PaymentCreateRequest request) {
        PaymentProvider provider = providerRegistry.require(request.provider());
        PaymentPreparation preparation = ledgerWriter.prepare(userId, request);
        if (!preparation.requiresProviderCall()) return ledgerWriter.get(preparation.paymentId());

        try {
            PaymentProviderResult result = provider.authorize(new PaymentProviderCommand(
                    preparation.paymentId(), preparation.reservationId(), request.paymentToken(),
                    request.idempotencyKey()));
            return ledgerWriter.complete(preparation.paymentId(), result);
        } catch (PaymentProviderException exception) {
            return ledgerWriter.fail(preparation.paymentId(), exception.getFailureCode());
        }
    }

    public PaymentResponse refund(Long ownerId, Long paymentId) {
        PaymentResponse payment = ledgerWriter.requireOwnedPaid(ownerId, paymentId);
        PaymentProvider provider = providerRegistry.require(payment.provider());
        try {
            provider.refund(payment.providerPaymentId(), payment.amountMinor(), payment.currency(),
                    "refund-" + payment.id());
            return ledgerWriter.refund(ownerId, paymentId);
        } catch (PaymentProviderException exception) {
            throw new PaymentException(PaymentErrorCode.PROVIDER_REJECTED);
        }
    }
}
