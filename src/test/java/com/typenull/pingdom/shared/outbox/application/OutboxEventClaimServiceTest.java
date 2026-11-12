package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 0, 0);
    @Mock private OutboxEventRepository repository;
    @Mock private OutboxMetrics metrics;
    private OutboxEventClaimService service;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(20, 2, 20, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        service = new OutboxEventClaimService(repository, properties, new OutboxBackoffPolicy(properties), metrics);
    }

    @Test
    void claimsOnlyReservedSlotsWithIndependentTokensAndDatabaseLease() {
        OutboxEvent first = event("1");
        OutboxEvent second = event("2");
        when(repository.databaseNow()).thenReturn(NOW);
        when(repository.findReadyEventsForUpdate(any(), eq(NOW), any())).thenReturn(List.of(first, second));

        List<OutboxClaim> claims = service.claimReadyEvents(2);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findReadyEventsForUpdate(any(), eq(NOW), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        assertThat(claims).extracting(OutboxClaim::claimToken).doesNotHaveDuplicates();
        assertThat(claims.getFirst().claimToken()).isEqualTo(first.getClaimToken());
        assertThat(first.getLeaseUntil()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void noCapacityDoesNotQueryDatabase() {
        assertThat(service.claimReadyEvents(0)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void backsOffExpiredLeaseAndClearsOldOwnership() {
        OutboxEvent event = event("1");
        event.claim("old", NOW.minusMinutes(9), NOW.minusMinutes(4));
        event.fail(NOW.minusMinutes(8), 5, NOW.minusMinutes(7), "first failure");
        event.claim("current", NOW.minusMinutes(6), NOW.minusMinutes(1));
        when(repository.databaseNow()).thenReturn(NOW);
        when(repository.findStaleProcessingEventsForUpdate(eq(OutboxEventStatus.PROCESSING), eq(NOW), any()))
                .thenReturn(List.of(event));

        assertThat(service.recoverStaleEvents()).isEqualTo(1);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getLeaseUntil()).isNull();
        verify(metrics).recordStaleRecovered(1);
    }

    private OutboxEvent event(String id) {
        return OutboxEvent.create("email:" + id, OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}", "USER", id, NOW.minusMinutes(10));
    }
}
