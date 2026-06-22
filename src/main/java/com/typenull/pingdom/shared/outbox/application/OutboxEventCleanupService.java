package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventCleanupService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final Clock outboxClock;

    @Transactional
    public int cleanupSucceededEvents() {
        LocalDateTime threshold = LocalDateTime.now(outboxClock).minus(properties.retention());
        return outboxEventRepository.deleteProcessedBefore(OutboxEventStatus.SUCCEEDED, threshold);
    }
}
