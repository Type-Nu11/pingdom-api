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

    /**
     * 고정 Clock과 조회 의존성을 연결하고 사용자 1을 활성 관광객으로 구성한다.
     */
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

    /**
     * 다른 관광객 2의 결제를 사용자 1이 조회하면 PAYMENT_FORBIDDEN인지 검증한다.
     */
    @Test
    void rejectsAnotherTouristPayment() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(processingPayment(2L)));

        assertThatThrownBy(() -> service.getMine(1L, 100L))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_FORBIDDEN));
    }

    /**
     * 없는 결제 단건 조회가 PAYMENT_NOT_FOUND로 거절되는지 검증한다.
     */
    @Test
    void rejectsUnknownPayment() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(1L, 100L))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 활성 계정이라도 MERCHANT_OWNER가 내 결제 목록을 요청하면 PAYMENT_FORBIDDEN인지 검증한다.
     */
    @Test
    void listMineRequiresTouristAccount() {
        User merchantOwner = User.builder().id(2L).role(UserRole.MERCHANT_OWNER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(merchantOwner));

        assertThatThrownBy(() -> service.listMine(2L, 1, 20))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_FORBIDDEN));
    }

    /**
     * 내 결제가 없으면 빈 배열과 페이지 1·크기 20·전체 0·다음 없음으로 응답하는지 검증한다.
     */
    @Test
    void returnsEmptyPaymentPage() {
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

    /**
     * 실패 결제 조회가 FAILED·DECLINED·실패 시각을 포함하고 공급자 ID·금액·통화·결제/환불 시각은 null인지 검증한다.
     */
    @Test
    void mapsFailedPaymentFields() {
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

    /**
     * 성공 결제 조회가 공급자 ID·15,000 KRW·결제 시각을 포함하며 실패 코드·실패/환불 시각은 null인지 검증한다.
     */
    @Test
    void mapsPaidPaymentFields() {
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

    /**
     * 주어진 관광객의 예약 10에 대해 고정된 생성 시각과 멱등 키로 PROCESSING 결제를 만든다.
     */
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
