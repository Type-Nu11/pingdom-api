package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;

public interface OutboxEventHandler {

    OutboxEventType supportedType();

    void handle(String eventId, String payload);
}
