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

    @BeforeEach
    void setUp() {
        service = new PaymentCommandService(registry, writer);
    }

    @Test
    void providerResultIsPersistedAsAuthoritativeAmount() {
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

    @Test
    void providerFailureIsRecordedWithoutCompletingPayment() {
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

    @Test
    void unknownProviderResultKeepsPaymentProcessing() {
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

    @Test
    void refundClaimsStateBeforeCallingProvider() {
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

    @Test
    void completedIdempotentRequestDoesNotCallProviderAgain() {
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TOSS", "token", "idem-1");
        PaymentResponse paid = response(PaymentStatus.PAID, 15_000L);
        when(registry.require("TOSS")).thenReturn(provider);
        when(writer.prepare(1L, request)).thenReturn(new PaymentPreparation(100L, 10L, PaymentStatus.PAID));
        when(writer.get(100L)).thenReturn(paid);

        PaymentResponse response = service.create(1L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(provider);
    }

    private PaymentResponse response(PaymentStatus status, Long amount) {
        return new PaymentResponse(100L, 10L, "TOSS", amount == null ? null : "provider-1", amount,
                amount == null ? null : "KRW", status, status == PaymentStatus.FAILED ? "DECLINED" : null,
                LocalDateTime.of(2026, 7, 26, 12, 0),
                status == PaymentStatus.PAID ? LocalDateTime.of(2026, 7, 26, 12, 1) : null, null, null);
    }
}
