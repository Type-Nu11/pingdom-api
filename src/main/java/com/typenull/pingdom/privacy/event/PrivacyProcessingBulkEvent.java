package com.typenull.pingdom.privacy.event;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import java.util.List;

public record PrivacyProcessingBulkEvent(
        List<Long> subjectUserIds,
        Long actorUserId,
        PrivacyProcessingActorType actorType,
        PrivacyProcessingAction action,
        String details
) {
    public PrivacyProcessingBulkEvent {
        subjectUserIds = List.copyOf(subjectUserIds);
    }

    public static PrivacyProcessingBulkEvent systemAction(
            List<Long> subjectUserIds,
            PrivacyProcessingAction action,
            String details
    ) {
        return new PrivacyProcessingBulkEvent(
                subjectUserIds,
                null,
                PrivacyProcessingActorType.SYSTEM,
                action,
                details
        );
    }
}
