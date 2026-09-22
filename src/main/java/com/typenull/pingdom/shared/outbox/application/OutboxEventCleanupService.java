package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 성공 이벤트의 보관 기간 경과분을 제한된 크기로 삭제. 실패/재시도 이력은 삭제 대상에서 제외. */
@Service
@RequiredArgsConstructor
public class OutboxEventCleanupService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final Clock outboxClock;

    /**
     * processedAt이 현재 시각에서 retention을 뺀 기준보다 이전인 성공 이벤트 한 배치를 삭제.
     * 반환값은 조회한 삭제 대상 ID 수이며 전체 잔여분의 반복 처리는 범위 외.
     */
    @Transactional
    public int cleanupSucceededEvents() {
        LocalDateTime threshold = LocalDateTime.now(outboxClock).minus(properties.retention());
        var eventIds = outboxEventRepository.findProcessedEventIdsBefore(
                OutboxEventStatus.SUCCEEDED,
                threshold,
                PageRequest.of(0, properties.cleanupBatchSize())
        );
        if (eventIds.isEmpty()) {
            return 0;
        }
        outboxEventRepository.deleteAllByIdInBatch(eventIds);
        return eventIds.size();
    }
}
