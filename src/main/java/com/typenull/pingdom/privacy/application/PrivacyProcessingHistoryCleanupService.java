package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacyProcessingHistoryCleanupService {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;
    private final PrivacyProcessingHistoryProperties properties;
    private final Clock clock;

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
