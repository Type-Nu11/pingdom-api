package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;

/** Outbox 재시도 시나리오의 이벤트·집계 식별자·시도 수·재시도 가능 여부와 원인 설명을 담는다. */
public record FixtureRetryEvent(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        long aggregateId,
        int attempts,
        boolean retryable,
        String diagnosticReason
) {
}
