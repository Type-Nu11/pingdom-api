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

/**
 * 개인정보 처리 주체·행위와 발생 시각 범위로 감사 이력을 최신순 조회.
 * 시각 범위 양 끝은 포함하며 페이지는 최소 1, 크기는 1~100으로 보정. 역전된 기간에 대한 별도 예외 검증은 미수행.
 */
@Service
@RequiredArgsConstructor
public class PrivacyProcessingHistoryQueryService {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    /**
     * 대상 사용자·행위자·행위와 양 끝을 포함한 선택 기간 조건으로 이력을 조회.
     * null 필터는 생략하고 생성 시각·ID 내림차순으로 페이지를 반환. 여기서는 조회자 권한을 검사하지 않으므로 호출자가 제한해야 함.
     */
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
                from != null,
                from,
                to != null,
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
