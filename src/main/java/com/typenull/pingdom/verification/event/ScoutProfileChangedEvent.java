package com.typenull.pingdom.verification.event;

import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import java.time.LocalDateTime;

public record ScoutProfileChangedEvent(
        Long actorUserId,
        Long scoutUserId,
        ScoutProfileStatus beforeStatus,
        ScoutProfileStatus afterStatus,
        LocalDateTime occurredAt
) {
}
