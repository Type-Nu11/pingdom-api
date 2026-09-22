package com.typenull.pingdom.verification.domain;

/** Scout 활동 자격의 심사·정지·만료·회수 상태다. ELIGIBLE이어도 기간과 프로필 활성 조건을 함께 확인해야 한다. */
public enum ScoutActivityEligibilityStatus {
    PENDING,
    ELIGIBLE,
    SUSPENDED,
    EXPIRED,
    REVOKED
}
