package com.typenull.pingdom.privacy.event;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;

public record PrivacyProcessingEvent(
        Long subjectUserId,
        Long actorUserId,
        PrivacyProcessingActorType actorType,
        PrivacyProcessingAction action,
        String details
) {
    public static PrivacyProcessingEvent userAction(
            Long userId,
            PrivacyProcessingAction action,
            String details
    ) {
        return new PrivacyProcessingEvent(userId, userId, PrivacyProcessingActorType.USER, action, details);
    }

    public static PrivacyProcessingEvent systemAction(
            Long subjectUserId,
            PrivacyProcessingAction action,
            String details
    ) {
        return new PrivacyProcessingEvent(subjectUserId, null, PrivacyProcessingActorType.SYSTEM, action, details);
    }
}
