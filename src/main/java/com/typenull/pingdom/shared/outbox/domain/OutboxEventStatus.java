package com.typenull.pingdom.shared.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SUCCEEDED,
    FAILED
}
