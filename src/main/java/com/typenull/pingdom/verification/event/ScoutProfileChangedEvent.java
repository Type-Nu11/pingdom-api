package com.typenull.pingdom.verification.event;

import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import java.time.LocalDateTime;

/** 프로필 상태 변경을 전달하는 이벤트. 최초 신청의 beforeStatus는 null이며 actor와 대상 Scout를 구분. */
public record ScoutProfileChangedEvent(
        Long actorUserId,
        Long scoutUserId,
        ScoutProfileStatus beforeStatus,
        ScoutProfileStatus afterStatus,
        LocalDateTime occurredAt
) {
}
