package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventSnapshot;
import com.typenull.pingdom.shared.observability.OutboxMetrics;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 업무 부작용의 멱등키는 eventId이며 선점 토큰은 Outbox 상태 갱신만 보호한다. */
@Component
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventStateService stateService;
    private final Map<OutboxEventType, OutboxEventHandler> handlers;
    private final OutboxMetrics outboxMetrics;
    private final OutboxLeaseManager leaseManager;

    public OutboxEventProcessor(OutboxEventStateService stateService, List<OutboxEventHandler> handlers,
            OutboxMetrics outboxMetrics, OutboxLeaseManager leaseManager) {
        this.stateService = stateService;
        this.outboxMetrics = outboxMetrics;
        this.leaseManager = leaseManager;
        this.handlers = new EnumMap<>(OutboxEventType.class);
        handlers.forEach(handler -> this.handlers.put(handler.supportedType(), handler));
    }

    public void process(OutboxClaim claim) {
        OutboxLeaseManager.Lease registration;
        try {
            registration = leaseManager.track(claim);
        } catch (RuntimeException exception) {
            stateService.releaseClaim(claim);
            outboxMetrics.recordLeaseEvent("registration_rejected");
            return;
        }
        try (OutboxLeaseManager.Lease lease = registration) {
            OutboxEventSnapshot event = stateService.findProcessingEvent(claim);
            if (event == null) {
                outboxMetrics.recordLeaseEvent("execution_skipped");
                return;
            }
            OutboxEventHandler handler = handlers.get(event.eventType());
            if (handler == null) {
                handleFailure(claim, event, lease,
                        new IllegalStateException("지원하지 않는 Outbox event type입니다."), "unsupported");
                return;
            }
            try {
                handler.handle(event.eventId(), event.payload());
            } catch (Exception exception) {
                handleFailure(claim, event, lease, exception, handlerName(handler));
                return;
            }
            if (lease.finish(() -> stateService.markSucceeded(claim))) {
                outboxMetrics.recordSuccess(event.eventType(), handlerName(handler));
                log.info("Outbox 처리 성공. eventId={}, eventType={}", event.eventId(), event.eventType());
            } else {
                outboxMetrics.recordLeaseEvent("completion_rejected");
            }
        }
    }

    private void handleFailure(OutboxClaim claim, OutboxEventSnapshot event, OutboxLeaseManager.Lease lease,
            Exception exception, String handlerName) {
        OutboxEventStatus status = lease.finish(() -> stateService.markFailed(claim, exception));
        if (status == null) {
            outboxMetrics.recordLeaseEvent("failure_rejected");
            return;
        }
        outboxMetrics.recordFailure(event.eventType(), handlerName, status);
        if (status == OutboxEventStatus.FAILED) {
            outboxMetrics.recordMaxAttemptsExceeded(event.eventType(), handlerName);
            log.error("Outbox 최대 재시도 초과. eventId={}, eventType={}",
                    event.eventId(), event.eventType(), exception);
        } else {
            log.warn("Outbox 처리 실패. 재시도 예정. eventId={}, eventType={}",
                    event.eventId(), event.eventType());
        }
    }

    private String handlerName(OutboxEventHandler handler) {
        return handler.getClass().getSimpleName();
    }
}
