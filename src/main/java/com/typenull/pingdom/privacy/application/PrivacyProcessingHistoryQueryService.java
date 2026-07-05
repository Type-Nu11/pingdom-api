package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.api.dto.PrivacyProcessingHistoryItem;
import com.typenull.pingdom.privacy.api.dto.PrivacyProcessingHistoryResponse;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacyProcessingHistoryQueryService {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    @Transactional(readOnly = true)
    public PrivacyProcessingHistoryResponse listHistories(
            Long subjectUserId,
            Long actorUserId,
            PrivacyProcessingAction action,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    ) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        var historyPage = privacyProcessingHistoryRepository.findByFilters(
                subjectUserId,
                actorUserId,
                action,
                from,
                to,
                pageable
        );
        List<PrivacyProcessingHistoryItem> histories = historyPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return PrivacyProcessingHistoryResponse.of(
                histories,
                safePage,
                safeLimit,
                historyPage.getTotalElements(),
                historyPage.getTotalPages()
        );
    }

    private PrivacyProcessingHistoryItem toItem(PrivacyProcessingHistory history) {
        return new PrivacyProcessingHistoryItem(
                history.getId(),
                history.getSubjectUserId(),
                history.getActorUserId(),
                history.getActorType(),
                history.getAction(),
                history.getDetails(),
                history.getRequestId(),
                history.getCreatedAt()
        );
    }
}
