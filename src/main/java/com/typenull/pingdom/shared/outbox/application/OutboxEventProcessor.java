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

/**
 * 선점된 이벤트의 handler를 호출하고 별도 상태 서비스로 성공·실패를 기록.
 * handler 부작용과 상태 저장은 원자적이지 않아 재처리될 수 있음.
 */
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

    /**
     * 현재 PROCESSING 스냅샷이 있을 때만 타입별 handler를 호출.
     * 지원 handler가 없거나 실행/성공 기록 중 예외가 발생하면 실패 처리로 전달.
     */
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

    /**
     * 재시도/최종 실패 상태를 기록하고 타입·handler별 실패 메트릭을 남김.
     * 최종 실패 로그에는 원본 예외를 포함하므로 payload의 민감 정보 제거는 보장 불가.
     */
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

    /** 메트릭과 실패 구분에 사용할 handler 클래스의 단순 이름을 반환. */
    private String handlerName(OutboxEventHandler handler) {
        return handler.getClass().getSimpleName();
    }
}
