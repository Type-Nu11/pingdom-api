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

    /**
     * 선점·처리·정리·상태 서비스와 executor 대역을 연결해 worker의 작업 제출만 검증한다.
     */
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

    /**
     * 선점된 이벤트 ID 2개에 대해 executor에 Runnable을 두 번 제출하는지 검증한다. 작업 본문 실행은 이 테스트 범위가 아니다.
     */
    @Test
    void claimedEventsAreSubmittedToExecutor() {
        when(claimService.claimReadyEvents()).thenReturn(List.of("event-1", "event-2"));

        worker.processReadyEvents();

        verify(outboxExecutor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    /**
     * 선점 후 executor가 작업을 거절하면 이벤트 ID와 TaskRejectedException을 상태 실패 처리에 전달하는지 검증한다.
     */
    @Test
    void returnsRejectedTaskToFailureFlow() {
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
