package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.infrastructure.PaymentTransactionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import com.typenull.pingdom.reservation.api.dto.ReservationCreateRequest;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationQuoteRepository;
import java.sql.Connection;
import java.time.*;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 연결은 mock으로 격리하고 실제 Spring 트랜잭션 프록시의 참여·rollback-only 동작을 검증. */
class ReservationQuoteTransactionTest {
    private ReservationQuoteService service;
    private TransactionTemplate transaction;
    private Connection connection;
    private ReservationQuote quote;
    private ReservationCreateRequest request;

    @BeforeEach
    void setup() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
        var confirmation = mock(ReservationConfirmation.class);
        when(confirmation.availabilityId()).thenReturn(9L);
        when(confirmation.quantity()).thenReturn(1);
        when(confirmation.expiresAt()).thenReturn(clock.instant());
        quote = ReservationQuote.issue(1L, confirmation, clock.instant().minusSeconds(300));
        request = new ReservationCreateRequest(9L, "intent", 1, "예약자", "01000000000", null, quote.getId());
        var quotes = mock(ReservationQuoteRepository.class);
        when(quotes.findByIdForUpdate(quote.getId())).thenReturn(Optional.of(quote));
        when(quotes.findByTouristUserIdAndIdempotencyKey(1L, "intent")).thenReturn(Optional.of(quote));
        var target = new ReservationQuoteService(quotes, mock(PlaceAvailabilityRepository.class),
                mock(ReservableProductRepository.class), mock(MapPlaceRepository.class), mock(UserRepository.class),
                mock(PaymentTransactionRepository.class), new ObjectMapper().findAndRegisterModules(), clock);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (ReservationQuoteService) proxy.getProxy();
    }

    @Test
    void verificationAndReplayRequireExistingTransaction() throws Exception {
        assertThatThrownBy(() -> service.verify(1L, request)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.checkPriorResult(1L, request)).isInstanceOf(IllegalTransactionStateException.class);
        verify(connection, never()).commit();
    }

    @Test
    void finalRejectionAndReplayKeepCallerTransactionCommittable() throws Exception {
        transaction.executeWithoutResult(status -> {
            assertThatThrownBy(() -> service.verify(1L, request)).isInstanceOfSatisfying(
                    ConfirmedReservationRejectedException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(ReservationErrorCode.QUOTE_EXPIRED));
            assertThatThrownBy(() -> service.checkPriorResult(1L, request))
                    .isInstanceOf(ConfirmedReservationRejectedException.class);
            assertThat(status.isRollbackOnly()).isFalse();
        });
        assertThat(quote.getRejectionCode()).isEqualTo(ReservationErrorCode.QUOTE_EXPIRED);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
