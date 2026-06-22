package com.typenull.pingdom.shared.outbox.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private OutboxEventClaimService claimService;

    @Mock
    private OutboxEventProcessor processor;

    @Mock
    private OutboxEventCleanupService cleanupService;

    @Mock
    private OutboxEventStateService stateService;

    @Mock
    private TaskExecutor outboxExecutor;

    private OutboxEventWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxEventWorker(
                claimService,
                processor,
                cleanupService,
                stateService,
                outboxExecutor
        );
    }

    @Test
    void claimedEventsAreSubmittedToExecutor() {
        when(claimService.claimReadyEvents()).thenReturn(List.of("event-1", "event-2"));

        worker.processReadyEvents();

        verify(outboxExecutor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void rejectedTaskReturnsClaimedEventToRetryFlow() {
        when(claimService.claimReadyEvents()).thenReturn(List.of("event-1"));
        doThrow(new TaskRejectedException("queue full"))
                .when(outboxExecutor)
                .execute(any(Runnable.class));

        worker.processReadyEvents();

        verify(stateService).markFailed(
                org.mockito.ArgumentMatchers.eq("event-1"),
                any(TaskRejectedException.class)
        );
    }
}
