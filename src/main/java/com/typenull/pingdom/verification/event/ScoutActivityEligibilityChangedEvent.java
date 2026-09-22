package com.typenull.pingdom.verification.event;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import java.time.LocalDateTime;

/** 활동 자격 심사에 따른 상태 변경 이벤트다. 행위자·대상·전후 상태와 발생 시각을 전달한다. */
public record ScoutActivityEligibilityChangedEvent(
        Long actorUserId,
        Long scoutUserId,
        ScoutActivityEligibilityStatus beforeStatus,
        ScoutActivityEligibilityStatus afterStatus,
        LocalDateTime occurredAt
) {
}
