package com.typenull.pingdom.verification.domain;

/** Scout 프로필의 심사 상태. 활동 기간의 유효성은 ACTIVE 여부와 별도 판정. */
public enum ScoutProfileStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED
}
