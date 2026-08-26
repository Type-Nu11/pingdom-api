package com.typenull.pingdom.verification.domain;

/** 체류 기반 방문 인증 세션의 서버 판정 상태입니다. */
public enum VisitVerificationSessionStatus {
    STARTED,
    IN_PROGRESS,
    PROXIMITY_LOST,
    COMPLETED,
    EXPIRED,
    REJECTED
}
