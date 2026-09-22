package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;

/** 이벤트 타입별 외부 부작용을 처리하는 계약이다. 재처리될 수 있으므로 구현체가 중복 실행을 고려해야 한다. */
public interface OutboxEventHandler {

    /** 이 구현이 처리할 단일 이벤트 타입을 반환한다. */
    OutboxEventType supportedType();

    /** 저장된 JSON payload를 처리한다. 실패를 예외로 전달하면 processor가 재시도 상태 전환을 시도한다. */
    void handle(String eventId, String payload);
}
