package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 시각에서 보관 개월 수를 뺀 기준보다 오래된 이력을 한 배치씩 삭제.
 * 기준 시각과 동일한 이력은 남기고, 반환값은 조회해 삭제를 요청한 ID 수.
 */
@Service
@RequiredArgsConstructor
public class PrivacyProcessingHistoryCleanupService {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;
    private final PrivacyProcessingHistoryProperties properties;
    private final Clock clock;

    /**
     * 보관 개월 수를 지난 이력을 오래된 순으로 한 배치 조회·삭제하고 삭제를 요청한 ID 수를 반환.
     * 기준 시각과 같은 이력은 남기고, 한 호출의 DB 변경만 같은 트랜잭션에 포함.
     */
    @Transactional
    public int cleanupExpiredHistories() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMonths(properties.retentionMonths());
        var historyIds = privacyProcessingHistoryRepository.findIdsCreatedBefore(
                threshold,
                PageRequest.of(0, properties.cleanupBatchSize())
        );
        if (historyIds.isEmpty()) {
            return 0;
        }

        privacyProcessingHistoryRepository.deleteAllByIdInBatch(historyIds);
        return historyIds.size();
    }
}
