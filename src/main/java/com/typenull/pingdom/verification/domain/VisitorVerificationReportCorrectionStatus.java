package com.typenull.pingdom.verification.domain;

/** 정정 요청의 심사 상태. 승인 시 원본 제보는 별도의 재심사 대기로 돌아감. */
public enum VisitorVerificationReportCorrectionStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED
}
