package com.typenull.pingdom.verification.domain;

public enum LocationCheckInStatus {
    /** 기존 단발 위치 제출로 반경만 확인된 기록입니다. */
    PROXIMITY_MATCHED,
    /** 서버가 체류 인증 세션의 시간·연속 위치 관측을 확인해 확정한 방문입니다. */
    DWELL_VERIFIED
}
