package com.typenull.pingdom.verification.domain;

/** 정정 요청의 심사 상태다. 승인 시 원본 제보는 별도의 재심사 대기로 돌아간다. */
public enum VisitorVerificationReportCorrectionStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED
}
