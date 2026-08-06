package com.typenull.pingdom.verification.event;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import java.time.LocalDateTime;

public record ScoutActivityEligibilityChangedEvent(
        Long actorUserId,
        Long scoutUserId,
        ScoutActivityEligibilityStatus beforeStatus,
        ScoutActivityEligibilityStatus afterStatus,
        LocalDateTime occurredAt
) {
}
