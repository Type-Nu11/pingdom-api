package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrivacyProcessingHistoryEventListener {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PrivacyProcessingEvent event) {
        try {
            privacyProcessingHistoryRepository.save(toHistory(
                    event.subjectUserId(),
                    event.actorUserId(),
                    event.actorType(),
                    event.action(),
                    event.details()
            ));
        } catch (Exception exception) {
            log.error(
                    "개인정보 처리 이력 저장에 실패했습니다. subjectUserId={}, actorUserId={}, actorType={}, action={}",
                    event.subjectUserId(),
                    event.actorUserId(),
                    event.actorType(),
                    event.action(),
                    exception
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PrivacyProcessingBulkEvent event) {
        try {
            List<PrivacyProcessingHistory> histories = event.subjectUserIds().stream()
                    .map(subjectUserId -> toHistory(
                            subjectUserId,
                            event.actorUserId(),
                            event.actorType(),
                            event.action(),
                            event.details()
                    ))
                    .toList();
            privacyProcessingHistoryRepository.saveAll(histories);
        } catch (Exception exception) {
            log.error(
                    "개인정보 처리 이력 벌크 저장에 실패했습니다. subjectUserCount={}, actorUserId={}, actorType={}, action={}",
                    event.subjectUserIds().size(),
                    event.actorUserId(),
                    event.actorType(),
                    event.action(),
                    exception
            );
        }
    }

    private PrivacyProcessingHistory toHistory(
            Long subjectUserId,
            Long actorUserId,
            PrivacyProcessingActorType actorType,
            PrivacyProcessingAction action,
            String details
    ) {
        return PrivacyProcessingHistory.builder()
                .subjectUserId(subjectUserId)
                .actorUserId(actorUserId)
                .actorType(actorType)
                .action(action)
                .details(details)
                .requestId(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                .createdAt(LocalDateTime.now(clock))
                .build();
    }
}
