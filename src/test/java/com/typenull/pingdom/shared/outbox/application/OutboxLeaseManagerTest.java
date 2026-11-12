package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxLeaseManagerTest {
    private static final OutboxClaim CLAIM = new OutboxClaim("event", "token");
    @Mock private OutboxEventStateService stateService;
    @Mock private ScheduledExecutorService scheduler;
    @Mock private ScheduledFuture<?> future;
    @Mock private OutboxMetrics metrics;
    private final AtomicLong nanos = new AtomicLong();
    private OutboxLeaseManager manager;
    private Runnable heartbeat;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(20, 2, 40, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any());
        manager = new OutboxLeaseManager(stateService, properties, scheduler, metrics, nanos::get);
        manager.track(CLAIM);
        heartbeat = task.getValue();
    }

    @AfterEach
    void clearCooperativeCancellation() {
        Thread.interrupted();
    }

    @Test
    void renewsUsingOriginalClaim() {
        when(stateService.renewClaim(CLAIM)).thenReturn(true);

        heartbeat.run();

        verify(stateService).renewClaim(CLAIM);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void transientDatabaseFailureCanRetryWithoutDeclaringOwnershipLost() {
        when(stateService.renewClaim(CLAIM)).thenThrow(new IllegalStateException("db")).thenReturn(true);

        heartbeat.run();
        heartbeat.run();

        verify(stateService, times(2)).renewClaim(CLAIM);
        verify(metrics).recordLeaseEvent("renewal_error");
        verify(metrics, never()).recordLeaseEvent("ownership_lost");
        verify(future, never()).cancel(false);
    }

    @Test
    void definitiveLossStopsRenewalAndRequestsCancellation() {
        heartbeat.run();
        heartbeat.run();

        verify(stateService, times(1)).renewClaim(CLAIM);
        verify(metrics).recordLeaseEvent("ownership_lost");
        verify(future).cancel(false);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void maximumExecutionStopsEvenIfDatabaseCannotBeContacted() {
        nanos.set(Duration.ofMinutes(30).toNanos());

        heartbeat.run();

        verifyNoInteractions(stateService);
        verify(metrics).recordLeaseEvent("max_execution_time");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void completionUnregistersBeforeStateChangeAndPreventsFalseLoss() {
        ArgumentCaptor<Runnable> nextTask = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(nextTask.capture(), anyLong(), anyLong(), any());
        OutboxLeaseManager.Lease lease = manager.track(CLAIM);

        assertThat(lease.finish(() -> {
            nextTask.getValue().run();
            return true;
        })).isTrue();

        verifyNoInteractions(stateService);
        verify(metrics, never()).recordLeaseEvent("ownership_lost");
        verify(future).cancel(false);
    }
}
