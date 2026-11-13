package com.typenull.pingdom.shared.outbox.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventSnapshot;
import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {
    private static final OutboxClaim CLAIM = new OutboxClaim("event-id", "original-token");
    @Mock private OutboxEventStateService stateService;
    @Mock private OutboxEventHandler handler;
    @Mock private OutboxMetrics metrics;
    @Mock private ScheduledExecutorService scheduler;
    @Mock private ScheduledFuture<?> future;
    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        when(handler.supportedType()).thenReturn(OutboxEventType.EMAIL_VERIFICATION_REQUESTED);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        OutboxProperties properties = new OutboxProperties(20, 2, 40, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        OutboxLeaseManager leases = new OutboxLeaseManager(stateService, properties, scheduler, metrics, () -> 0L);
        processor = new OutboxEventProcessor(stateService, List.of(handler), metrics, leases);
    }

    @Test
    void countsSuccessOnlyWhenOriginalClaimCompletes() {
        when(stateService.findProcessingEvent(CLAIM)).thenReturn(snapshot());
        when(stateService.markSucceeded(CLAIM)).thenReturn(true);

        processor.process(CLAIM);

        verify(handler).handle(CLAIM.eventId(), "{}");
        verify(metrics).recordSuccess(eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED), any());
        verify(future, atLeastOnce()).cancel(false);
    }

    @Test
    void lateSuccessIsNotCountedAsSuccess() {
        when(stateService.findProcessingEvent(CLAIM)).thenReturn(snapshot());
        when(stateService.markSucceeded(CLAIM)).thenReturn(false);

        processor.process(CLAIM);

        verify(metrics, never()).recordSuccess(any(), any());
        verify(metrics).recordLeaseEvent("completion_rejected");
    }

    @Test
    void handlerFailureCountsOnlyAppliedRetry() {
        when(stateService.findProcessingEvent(CLAIM)).thenReturn(snapshot());
        doThrow(new IllegalStateException("temporary")).when(handler).handle(CLAIM.eventId(), "{}");
        when(stateService.markFailed(eq(CLAIM), any())).thenReturn(OutboxEventStatus.RETRY);

        processor.process(CLAIM);

        verify(metrics).recordFailure(eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED), any(),
                eq(OutboxEventStatus.RETRY));
    }

    @Test
    void lateFailureIsNotCountedAsBusinessFailure() {
        when(stateService.findProcessingEvent(CLAIM)).thenReturn(snapshot());
        doThrow(new IllegalStateException("late")).when(handler).handle(CLAIM.eventId(), "{}");

        processor.process(CLAIM);

        verify(metrics, never()).recordFailure(any(), any(), any());
        verify(metrics).recordLeaseEvent("failure_rejected");
    }

    @Test
    void missingOwnershipSkipsHandler() {
        processor.process(CLAIM);

        verify(handler, never()).handle(any(), any());
        verify(metrics).recordLeaseEvent("execution_skipped");
    }

    @Test
    void heartbeatRegistrationRejectionReturnsUnstartedClaim() {
        reset(scheduler);
        doThrow(new java.util.concurrent.RejectedExecutionException("shutdown"))
                .when(scheduler).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

        processor.process(CLAIM);

        verify(stateService).releaseClaim(CLAIM);
        verify(stateService, never()).markFailed(any(), any());
        verify(handler, never()).handle(any(), any());
        verify(metrics).recordLeaseEvent("registration_rejected");
    }

    private OutboxEventSnapshot snapshot() {
        return new OutboxEventSnapshot(CLAIM.eventId(), OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}", "USER", "1", 0);
    }
}
