package com.typenull.pingdom.shared.outbox.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventSnapshot;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    private static final String EVENT_ID = "event-id";

    @Mock
    private OutboxEventStateService stateService;

    @Mock
    private OutboxEventHandler handler;

    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        when(handler.supportedType()).thenReturn(OutboxEventType.EMAIL_VERIFICATION_REQUESTED);
        processor = new OutboxEventProcessor(stateService, List.of(handler));
    }

    @Test
    void successfulHandlerMarksEventSucceeded() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(snapshot());

        processor.process(EVENT_ID);

        verify(handler).handle(EVENT_ID, "{}");
        verify(stateService).markSucceeded(EVENT_ID);
    }

    @Test
    void temporaryFailureMarksEventForRetry() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(snapshot());
        doThrow(new IllegalStateException("temporary")).when(handler).handle(EVENT_ID, "{}");
        when(stateService.markFailed(eq(EVENT_ID), any(IllegalStateException.class)))
                .thenReturn(OutboxEventStatus.RETRY);

        processor.process(EVENT_ID);

        verify(stateService).markFailed(eq(EVENT_ID), any(IllegalStateException.class));
    }

    @Test
    void retriedEventSucceedsAfterTemporaryFailure() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(snapshot());
        doThrow(new IllegalStateException("temporary"))
                .doNothing()
                .when(handler)
                .handle(EVENT_ID, "{}");
        when(stateService.markFailed(eq(EVENT_ID), any(IllegalStateException.class)))
                .thenReturn(OutboxEventStatus.RETRY);

        processor.process(EVENT_ID);
        processor.process(EVENT_ID);

        verify(handler, times(2)).handle(EVENT_ID, "{}");
        verify(stateService).markSucceeded(EVENT_ID);
    }

    @Test
    void alreadyCompletedEventIsNotHandledAgain() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(null);

        processor.process(EVENT_ID);

        verify(handler, never()).handle(any(), any());
    }

    private OutboxEventSnapshot snapshot() {
        return new OutboxEventSnapshot(
                EVENT_ID,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                "1",
                0
        );
    }
}
