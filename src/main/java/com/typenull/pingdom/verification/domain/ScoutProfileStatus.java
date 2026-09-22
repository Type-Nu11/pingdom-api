package com.typenull.pingdom.verification.domain;

/** Scout 프로필의 심사 상태다. ACTIVE만으로 활동 기간까지 유효하다는 의미는 아니다. */
public enum ScoutProfileStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED
}
