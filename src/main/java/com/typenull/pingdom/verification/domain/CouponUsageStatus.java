package com.typenull.pingdom.verification.domain;

/** 방문자가 제보한 쿠폰 사용 가능 상태다. UNKNOWN은 확인되지 않음을 뜻하며 실제 쿠폰 권한 판정은 아니다. */
public enum CouponUsageStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}
