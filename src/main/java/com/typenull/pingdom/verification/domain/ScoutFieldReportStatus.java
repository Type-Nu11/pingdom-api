package com.typenull.pingdom.verification.domain;

/** 현장 제보의 제출·승인·거절 상태. 심사가 끝난 제보의 재심사는 도메인에서 거부. */
public enum ScoutFieldReportStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED
}
