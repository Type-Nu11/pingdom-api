package com.typenull.pingdom.privacy.api.dto;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import java.time.LocalDateTime;

public record PrivacyProcessingHistoryItem(
        Long id,
        Long subjectUserId,
        Long actorUserId,
        PrivacyProcessingActorType actorType,
        PrivacyProcessingAction action,
        String details,
        String requestId,
        LocalDateTime createdAt
) {
}
