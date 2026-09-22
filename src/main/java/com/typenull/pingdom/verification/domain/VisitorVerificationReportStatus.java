package com.typenull.pingdom.verification.domain;

/** 방문 제보의 심사 상태. 승인·거절된 제보도 정정 승인 후 SUBMITTED로 돌아갈 수 있음. */
public enum VisitorVerificationReportStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED
}
