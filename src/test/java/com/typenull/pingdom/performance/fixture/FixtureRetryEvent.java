package com.typenull.pingdom.performance.fixture;

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
