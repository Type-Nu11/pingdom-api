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

@Component
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventStateService stateService;
    private final Map<OutboxEventType, OutboxEventHandler> handlers;
    private final OutboxMetrics outboxMetrics;

    public OutboxEventProcessor(
            OutboxEventStateService stateService,
            List<OutboxEventHandler> handlers,
            OutboxMetrics outboxMetrics
    ) {
        this.stateService = stateService;
        this.outboxMetrics = outboxMetrics;
        this.handlers = new EnumMap<>(OutboxEventType.class);
        handlers.forEach(handler -> this.handlers.put(handler.supportedType(), handler));
    }

    public void process(String eventId) {
        OutboxEventSnapshot event = stateService.findProcessingEvent(eventId);
        if (event == null) {
            return;
        }

        OutboxEventHandler handler = handlers.get(event.eventType());
        if (handler == null) {
            handleFailure(event, new IllegalStateException("지원하지 않는 Outbox event type입니다."), "unsupported");
            return;
        }

        try {
            handler.handle(event.eventId(), event.payload());
            stateService.markSucceeded(event.eventId());
            outboxMetrics.recordSuccess(event.eventType(), handlerName(handler));
            log.info(
                    "Outbox 처리 성공. eventId={}, eventType={}, aggregateType={}, aggregateId={}",
                    event.eventId(),
                    event.eventType(),
                    event.aggregateType(),
                    event.aggregateId()
            );
        } catch (Exception exception) {
            handleFailure(event, exception, handlerName(handler));
        }
    }

    private void handleFailure(OutboxEventSnapshot event, Exception exception, String handlerName) {
        OutboxEventStatus status = stateService.markFailed(event.eventId(), exception);
        outboxMetrics.recordFailure(event.eventType(), handlerName, status);
        if (status == OutboxEventStatus.FAILED) {
            outboxMetrics.recordMaxAttemptsExceeded(event.eventType(), handlerName);
            log.error(
                    "Outbox 최대 재시도 초과. eventId={}, eventType={}, aggregateType={}, aggregateId={}, reason={}",
                    event.eventId(),
                    event.eventType(),
                    event.aggregateType(),
                    event.aggregateId(),
                    exception.getMessage(),
                    exception
            );
            return;
        }

        log.warn(
                "Outbox 처리 실패. 재시도 예정. eventId={}, eventType={}, aggregateType={}, aggregateId={}, reason={}",
                event.eventId(),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId(),
                exception.getMessage()
        );
    }

    private String handlerName(OutboxEventHandler handler) {
        return handler.getClass().getSimpleName();
    }
}
