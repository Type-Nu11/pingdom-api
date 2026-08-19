package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;

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
