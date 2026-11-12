package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class OutboxEventWorkerTest {
    private static final OutboxClaim FIRST = new OutboxClaim("first", "first-token");
    private static final OutboxClaim SECOND = new OutboxClaim("second", "second-token");
    @Mock private OutboxEventClaimService claimService;
    @Mock private OutboxEventProcessor processor;
    @Mock private OutboxEventCleanupService cleanupService;
    @Mock private OutboxEventStateService stateService;
    @Mock private TaskExecutor executor;
    private OutboxEventWorker worker;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(20, 2, 40, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
        worker = new OutboxEventWorker(claimService, processor, cleanupService, stateService, executor, properties);
    }

    @Test
    void reservesCapacityUntilRunnableActuallyTerminates() {
        List<Runnable> submitted = new ArrayList<>();
        doAnswer(invocation -> { submitted.add(invocation.getArgument(0)); return null; })
                .when(executor).execute(any());
        when(claimService.claimReadyEvents(2)).thenReturn(List.of(FIRST, SECOND));
        when(claimService.claimReadyEvents(1)).thenReturn(List.of());

        worker.processReadyEvents();
        worker.processReadyEvents();
        verify(claimService, times(1)).claimReadyEvents(2);
        assertThat(submitted).hasSize(2);

        submitted.getFirst().run();
        verify(processor).process(FIRST);
        worker.processReadyEvents();
        verify(claimService).claimReadyEvents(1);
    }

    @Test
    void rejectionReturnsClaimWithoutBusinessFailureAndReleasesSlot() {
        when(claimService.claimReadyEvents(2)).thenReturn(List.of(FIRST), List.of());
        doThrow(new TaskRejectedException("full")).when(executor).execute(any());

        worker.processReadyEvents();
        worker.processReadyEvents();

        verify(stateService).releaseClaim(FIRST);
        verify(stateService, never()).markFailed(any(), any());
        verify(claimService, times(2)).claimReadyEvents(2);
        verifyNoInteractions(processor);
    }

    @Test
    void claimFailureReturnsReservedCapacity() {
        when(claimService.claimReadyEvents(2)).thenThrow(new IllegalStateException("db"))
                .thenReturn(List.of());

        assertThatThrownBy(worker::processReadyEvents).isInstanceOf(IllegalStateException.class);
        worker.processReadyEvents();

        verify(claimService, times(2)).claimReadyEvents(2);
    }
}
