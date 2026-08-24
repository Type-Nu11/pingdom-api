package com.typenull.pingdom.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.api.dto.PaymentPageResponse;
import com.typenull.pingdom.payment.api.dto.PaymentResponse;
import com.typenull.pingdom.payment.domain.PaymentStatus;
import com.typenull.pingdom.payment.domain.PaymentTransaction;
import com.typenull.pingdom.payment.domain.exception.PaymentErrorCode;
import com.typenull.pingdom.payment.domain.exception.PaymentException;
import com.typenull.pingdom.payment.infrastructure.PaymentTransactionRepository;
import com.typenull.pingdom.payment.infrastructure.SettlementLedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class PaymentQueryServiceTest {

    private final PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
    private final SettlementLedgerRepository ledgerRepository = mock(SettlementLedgerRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AvailabilityAccessPolicy availabilityAccessPolicy = mock(AvailabilityAccessPolicy.class);
    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(
                paymentRepository,
                ledgerRepository,
                userRepository,
                availabilityAccessPolicy,
                Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC)
        );
        User tourist = User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
    }

    @Test
    void getMineRejectsAnotherTouristPayment() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(processingPayment(2L)));

        assertThatThrownBy(() -> service.getMine(1L, 100L))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_FORBIDDEN));
    }

    @Test
    void getMineReturnsNotFoundForUnknownPayment() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(1L, 100L))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void listMineRequiresTouristAccount() {
        User merchantOwner = User.builder().id(2L).role(UserRole.MERCHANT_OWNER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(merchantOwner));

        assertThatThrownBy(() -> service.listMine(2L, 1, 20))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_FORBIDDEN));
    }

    @Test
    void listMineReturnsEmptyPageInsteadOfNotFound() {
        when(paymentRepository.findAllByTouristUserId(eq(1L), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        PaymentPageResponse response = service.listMine(1L, 1, 20);

        assertThat(response.payments()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(20);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getMineExposesFailureTimestampOnlyForFailedPayment() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 20, 13, 5);
        PaymentTransaction payment = processingPayment(1L);
        payment.fail("DECLINED", failedAt);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getMine(1L, 100L);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("DECLINED");
        assertThat(response.failedAt()).isEqualTo(failedAt);
        assertThat(response.providerPaymentId()).isNull();
        assertThat(response.amountMinor()).isNull();
        assertThat(response.currency()).isNull();
        assertThat(response.paidAt()).isNull();
        assertThat(response.refundedAt()).isNull();
    }

    @Test
    void getMineDoesNotExposeFailureFieldsForPaidPayment() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 20, 13, 5);
        PaymentTransaction payment = processingPayment(1L);
        payment.succeed("provider-1", 15_000L, "KRW", paidAt);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getMine(1L, 100L);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.providerPaymentId()).isEqualTo("provider-1");
        assertThat(response.amountMinor()).isEqualTo(15_000L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.paidAt()).isEqualTo(paidAt);
        assertThat(response.failureCode()).isNull();
        assertThat(response.failedAt()).isNull();
        assertThat(response.refundedAt()).isNull();
    }

    private PaymentTransaction processingPayment(Long touristUserId) {
        return PaymentTransaction.processing(
                10L,
                touristUserId,
                3L,
                "TOSS",
                "request-1",
                LocalDateTime.of(2026, 7, 20, 13, 0)
        );
    }
}
