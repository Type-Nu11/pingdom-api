package com.typenull.pingdom.verification.domain;

/** 방문자가 제보하는 혼잡도 단계. 실제 수용 인원과 예약 가능 수량 계산은 용도 외. */
public enum CrowdLevel {
    LOW,
    MODERATE,
    HIGH,
    FULL
}
