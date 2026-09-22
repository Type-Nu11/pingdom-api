package com.typenull.pingdom.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.payment.api.dto.PaymentCreateRequest;
import com.typenull.pingdom.payment.api.dto.PaymentResponse;
import com.typenull.pingdom.payment.application.provider.*;
import com.typenull.pingdom.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentCommandServiceTest {
    private final PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
    private final PaymentLedgerWriter writer = mock(PaymentLedgerWriter.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private PaymentCommandService service;

    /**
     * 결제 공급자 조회와 원장 기록을 mock으로 분리한 결제 명령 서비스를 구성.
     */
    @BeforeEach
    void setUp() {
        service = new PaymentCommandService(registry, writer);
    }

    /**
     * 처리 중 결제에 공급자 명령을 전달하고 완료 기록에서 반환한 15,000 금액을 응답하는지 검증.
     * 실제 DB 저장과 공급자 결제 실행은 검증 범위에서 제외.
     */
    @Test
    void usesProviderResultAmount() {
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TOSS", "token", "idem-1");
        PaymentProviderResult result = new PaymentProviderResult("provider-1", 15_000L, 450L, "KRW");
        PaymentResponse completed = response(PaymentStatus.PAID, 15_000L);
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.prepare(1L, request)).thenReturn(new PaymentPreparation(100L, 10L, PaymentStatus.PROCESSING));
        when(provider.authorize(any())).thenReturn(result);
        when(writer.complete(100L, result)).thenReturn(completed);

        PaymentResponse response = service.create(1L, request);

        assertThat(response.amountMinor()).isEqualTo(15_000L);
        verify(provider).authorize(new PaymentProviderCommand(100L, 10L, "token", "idem-1"));
    }

    /**
     * 공급자가 DECLINED 실패를 반환하면 FAILED 응답으로 처리하고 결제 완료 기록을 호출하지 않는지 검증.
     */
    @Test
    void recordsDeclinedProviderPayment() {
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TOSS", "token", "idem-1");
        PaymentResponse failed = response(PaymentStatus.FAILED, null);
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.prepare(1L, request)).thenReturn(new PaymentPreparation(100L, 10L, PaymentStatus.PROCESSING));
        when(provider.authorize(any())).thenThrow(new PaymentProviderException(
                PaymentProviderFailureType.DECLINED, "DECLINED", "declined"));
        when(writer.fail(100L, "DECLINED")).thenReturn(failed);

        PaymentResponse response = service.create(1L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(writer, never()).complete(anyLong(), any());
    }

    /**
     * 공급자 TIMEOUT으로 결과가 UNKNOWN이면 PROVIDER_RESULT_UNKNOWN을 던지고 실패로 확정 기록하지 않는지 검증.
     */
    @Test
    void preservesUnknownPaymentResult() {
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TOSS", "token", "idem-1");
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.prepare(1L, request)).thenReturn(new PaymentPreparation(100L, 10L, PaymentStatus.PROCESSING));
        when(provider.authorize(any())).thenThrow(new PaymentProviderException(
                PaymentProviderFailureType.UNKNOWN, "TIMEOUT", "timeout"));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(com.typenull.pingdom.payment.domain.exception.PaymentException.class)
                .extracting("errorCode")
                .isEqualTo(com.typenull.pingdom.payment.domain.exception.PaymentErrorCode.PROVIDER_RESULT_UNKNOWN);
        verify(writer, never()).fail(anyLong(), anyString());
    }

    /**
     * 환불 준비 결과의 공급자 결제 ID·금액·통화와 refund-100 키로 공급자를 호출하고 REFUNDED 응답을 반환하는지 검증.
     * 호출 순서는 직접 검증 대상에서 제외.
     */
    @Test
    void forwardsPreparedRefund() {
        PaymentResponse processing = response(PaymentStatus.REFUND_PROCESSING, 15_000L);
        PaymentResponse refunded = new PaymentResponse(100L, 10L, "TOSS", "provider-1", 15_000L,
                "KRW", PaymentStatus.REFUNDED, null, LocalDateTime.of(2026, 7, 26, 12, 0),
                LocalDateTime.of(2026, 7, 26, 12, 1), null, LocalDateTime.of(2026, 7, 26, 12, 2));
        when(writer.prepareRefund(2L, 100L)).thenReturn(processing);
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.completeRefund(2L, 100L)).thenReturn(refunded);

        PaymentResponse response = service.refund(2L, 100L);

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(provider).refund("provider-1", 15_000L, "KRW", "refund-100");
    }

    /**
     * 멱등 키가 포함된 요청의 준비 결과가 PAID이면 응답 상태도 PAID이며 결제 공급자를 호출하지 않는지 검증.
     */
    @Test
    void reusesCompletedIdempotentPayment() {
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TOSS", "token", "idem-1");
        PaymentResponse paid = response(PaymentStatus.PAID, 15_000L);
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.prepare(1L, request)).thenReturn(new PaymentPreparation(100L, 10L, PaymentStatus.PAID));
        when(writer.get(100L)).thenReturn(paid);

        PaymentResponse response = service.create(1L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(provider);
    }

    /**
     * 결제 상태·금액에 따라 공급자 ID·통화·실패 코드·결제 시각을 채운 응답 대역을 생성.
     */
    private PaymentResponse response(PaymentStatus status, Long amount) {
        return new PaymentResponse(100L, 10L, "TOSS", amount == null ? null : "provider-1", amount,
                amount == null ? null : "KRW", status, status == PaymentStatus.FAILED ? "DECLINED" : null,
                LocalDateTime.of(2026, 7, 26, 12, 0),
                status == PaymentStatus.PAID ? LocalDateTime.of(2026, 7, 26, 12, 1) : null, null, null);
    }
}
