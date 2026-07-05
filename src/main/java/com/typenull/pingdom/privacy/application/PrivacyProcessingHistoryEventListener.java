package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PrivacyProcessingHistoryEventListener {

    private final PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PrivacyProcessingEvent event) {
        privacyProcessingHistoryRepository.save(PrivacyProcessingHistory.builder()
                .subjectUserId(event.subjectUserId())
                .actorUserId(event.actorUserId())
                .actorType(event.actorType())
                .action(event.action())
                .details(event.details())
                .requestId(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }
}
