package com.typenull.pingdom.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.api.dto.PaymentCreateRequest;
import com.typenull.pingdom.payment.application.provider.PaymentProviderResult;
import com.typenull.pingdom.payment.domain.*;
import com.typenull.pingdom.payment.domain.exception.*;
import com.typenull.pingdom.payment.infrastructure.*;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;

class PaymentQuoteVerificationTest {
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final SettlementLedgerRepository ledger = mock(SettlementLedgerRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
    private final PaymentLedgerWriter writer = new PaymentLedgerWriter(payments, ledger, reservations, users, clock);
    private Reservation reservation;
    private PaymentTransaction payment;
    private com.fasterxml.jackson.databind.node.ObjectNode legacyConfirmation;

    @BeforeEach
    void setup() {
        reservation = Reservation.create(1L, 9L, "booking", 2, LocalDateTime.now(clock));
        org.springframework.test.util.ReflectionTestUtils.setField(reservation, "id", 10L);
        legacyConfirmation = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("totalAmountMinor", 2050L).put("currency", "KRW");
        org.springframework.test.util.ReflectionTestUtils.setField(reservation, "legacyConfirmation", legacyConfirmation);
        payment = PaymentTransaction.processing(10L, 1L, 7L, "TEST", "payment", LocalDateTime.now(clock));
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "id", 100L);
        when(payments.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(reservations.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservations.findByIdForUpdate(10L)).thenReturn(Optional.of(reservation));
    }

    @Test
    void amountMismatchPreservesProcessingAndDoesNotPostLedger() {
        rejects(new PaymentProviderResult("provider", 2000L, 0L, "KRW"));
    }

    @Test
    void currencyMismatchPreservesProcessingAndDoesNotPostLedger() {
        rejects(new PaymentProviderResult("provider", 2050L, 0L, "USD"));
    }

    private void rejects(PaymentProviderResult result) {
        assertThatThrownBy(() -> writer.complete(100L, result)).isInstanceOfSatisfying(PaymentException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_QUOTE_MISMATCH));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verifyNoInteractions(ledger);
    }

    @Test
    void exactAcceptedAmountCanBecomePaid() {
        writer.complete(100L, new PaymentProviderResult("provider", 2050L, 0L, "KRW"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(ledger).save(argThat(entry -> entry.getGrossAmountMinor() == 2050L && entry.getCurrency().equals("KRW")));
    }

    @Test
    void existingProcessingPaymentPropagatesLegacyAmountAndNewPaymentsAreDisabled() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        PaymentCreateRequest request = new PaymentCreateRequest(10L, "TEST", "token", "payment");
        when(payments.findByTouristUserIdAndIdempotencyKey(1L, "payment")).thenReturn(Optional.of(payment));
        var prepared = writer.prepare(1L, request);
        assertThat(prepared.expectedAmountMinor()).isEqualTo(2050L);
        assertThat(prepared.expectedCurrency()).isEqualTo("KRW");
        when(payments.findByTouristUserIdAndIdempotencyKey(1L, "payment")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> writer.prepare(1L, request)).isInstanceOfSatisfying(PaymentException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.RESERVATION_NOT_PAYABLE));
        verify(payments, never()).save(any());
        verifyNoInteractions(ledger);
    }

    @Test
    void malformedLegacyAmountDoesNotBecomePaid() {
        legacyConfirmation.remove("totalAmountMinor");
        rejects(new PaymentProviderResult("provider", 2050L, 0L, "KRW"));
    }
}
