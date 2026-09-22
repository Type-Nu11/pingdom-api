package com.typenull.pingdom.verification.domain;

/** 방문자가 제보한 쿠폰 사용 가능 상태. UNKNOWN은 확인되지 않음을 뜻하며 실제 쿠폰 권한 판정과는 별도. */
public enum CouponUsageStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}
