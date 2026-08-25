package com.typenull.pingdom.privacy.application;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import java.time.LocalDateTime;

record PrivacyProcessingOutboxPayload(
        Long subjectUserId,
        Long actorUserId,
        PrivacyProcessingActorType actorType,
        PrivacyProcessingAction action,
        String details,
        String requestId,
        LocalDateTime occurredAt
) {
}
