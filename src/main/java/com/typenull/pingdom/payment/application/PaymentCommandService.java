package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.payment.api.dto.PaymentCreateRequest;
import com.typenull.pingdom.payment.api.dto.PaymentResponse;
import com.typenull.pingdom.payment.application.provider.*;
import com.typenull.pingdom.payment.domain.exception.PaymentErrorCode;
import com.typenull.pingdom.payment.domain.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 준비·완료 트랜잭션 사이에서 외부 결제 사업자를 호출합니다.
 * 사업자 호출과 DB 반영은 하나의 원자적 작업이 아니며, 응답 불명은 실패로 확정하지 않고 처리 중 상태를 남깁니다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCommandService {
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentLedgerWriter ledgerWriter;

    /**
     * 같은 멱등 키의 완료 거래는 재사용하고, PROCESSING 거래는 같은 키로 사업자 승인 호출을 다시 수행합니다.
     * 중복 승인의 방지는 PaymentProvider의 멱등 계약에 의존하며, 확정 거절만 FAILED로 기록합니다.
     */
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
            if (exception.getFailureType() == PaymentProviderFailureType.DECLINED) {
                return ledgerWriter.fail(preparation.paymentId(), exception.getFailureCode());
            }
            throw new PaymentException(PaymentErrorCode.PROVIDER_RESULT_UNKNOWN);
        }
    }

    /**
     * 환불 처리 중 상태를 먼저 저장하고 거래 ID 기반 키로 전액 환불을 요청합니다.
     * 확정 거절이면 PAID로 되돌리지만, 결과 불명 또는 외부 성공 후 DB 실패를 여기서 자동 복구하지 않습니다.
     */
    public PaymentResponse refund(Long ownerId, Long paymentId) {
        PaymentResponse payment = ledgerWriter.prepareRefund(ownerId, paymentId);
        PaymentProvider provider = providerRegistry.require(payment.provider());
        try {
            provider.refund(payment.providerPaymentId(), payment.amountMinor(), payment.currency(),
                    "refund-" + payment.id());
            return ledgerWriter.completeRefund(ownerId, paymentId);
        } catch (PaymentProviderException exception) {
            if (exception.getFailureType() == PaymentProviderFailureType.DECLINED) {
                ledgerWriter.cancelRefund(ownerId, paymentId);
                throw new PaymentException(PaymentErrorCode.PROVIDER_REJECTED);
            }
            throw new PaymentException(PaymentErrorCode.PROVIDER_RESULT_UNKNOWN);
        }
    }
}
