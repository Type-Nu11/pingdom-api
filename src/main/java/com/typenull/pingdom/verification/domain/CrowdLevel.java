package com.typenull.pingdom.verification.domain;

/** 방문자가 제보하는 혼잡도 단계다. 실제 수용 인원이나 예약 가능 수량을 계산하는 값은 아니다. */
public enum CrowdLevel {
    LOW,
    MODERATE,
    HIGH,
    FULL
}
