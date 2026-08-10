package com.typenull.pingdom.moderation.application.query.outbox;

import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventResponse;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;

public interface AdminOutboxEventQueryService {

    AdminOutboxEventResponse list(
            Long adminUserId,
            OutboxEventStatus status,
            OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    );
}
