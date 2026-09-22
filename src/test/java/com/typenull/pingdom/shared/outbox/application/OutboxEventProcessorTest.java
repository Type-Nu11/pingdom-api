package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventSnapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
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

    @Mock
    private OutboxMetrics outboxMetrics;

    private OutboxEventProcessor processor;

    /**
     * 이메일 인증 이벤트를 지원하는 핸들러와 상태·메트릭 대역으로 processor를 구성한다.
     */
    @BeforeEach
    void setUp() {
        when(handler.supportedType()).thenReturn(OutboxEventType.EMAIL_VERIFICATION_REQUESTED);
        processor = new OutboxEventProcessor(stateService, List.of(handler), outboxMetrics);
    }

    /**
     * PROCESSING snapshot의 payload를 핸들러가 처리한 뒤 성공 상태 갱신과 이벤트 타입별 성공 메트릭이 호출되는지 검증한다.
     */
    @Test
    void successfulHandlerMarksEventSucceeded() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(snapshot());

        processor.process(EVENT_ID);

        verify(handler).handle(EVENT_ID, "{}");
        verify(stateService).markSucceeded(EVENT_ID);
        verify(outboxMetrics).recordSuccess(eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED), any());
    }

    /**
     * 핸들러의 임시 예외를 상태 서비스에 전달하고 반환된 RETRY 상태를 실패 메트릭에 기록하는지 검증한다.
     */
    @Test
    void temporaryFailureMarksEventForRetry() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(snapshot());
        doThrow(new IllegalStateException("temporary")).when(handler).handle(EVENT_ID, "{}");
        when(stateService.markFailed(eq(EVENT_ID), any(IllegalStateException.class)))
                .thenReturn(OutboxEventStatus.RETRY);

        processor.process(EVENT_ID);

        verify(stateService).markFailed(eq(EVENT_ID), any(IllegalStateException.class));
        verify(outboxMetrics).recordFailure(eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED), any(), eq(OutboxEventStatus.RETRY));
    }

    /**
     * 첫 핸들러 호출이 실패하고 두 번째가 성공하면 총 두 번 처리하고 최종 성공 갱신을 호출하는지 검증한다.
     */
    @Test
    void succeedsAfterTemporaryHandlerFailure() {
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

    /**
     * 상태 서비스가 처리 가능한 snapshot을 반환하지 않으면 핸들러를 실행하지 않아 완료된 이벤트의 중복 처리를 방지하는지 검증한다.
     */
    @Test
    void skipsMissingProcessingSnapshot() {
        when(stateService.findProcessingEvent(EVENT_ID)).thenReturn(null);

        processor.process(EVENT_ID);

        verify(handler, never()).handle(any(), any());
    }

    /**
     * 이메일 인증 타입·빈 JSON payload·사용자 집계·시도 0회를 가진 처리 입력 snapshot을 제공한다.
     */
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
