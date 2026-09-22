package com.typenull.pingdom.verification.domain;

/** 방문 제보의 분류로 대기 시간·언어·쿠폰 사용·혼잡도는 유형에 맞는 구조화 값 하나를 요구. */
public enum VisitorVerificationReportType {
    PLACE_INFORMATION,
    OPERATING_HOURS,
    LOCATION,
    CLOSED_PLACE,
    WAIT_TIME,
    LANGUAGE_SUPPORT,
    COUPON_USAGE,
    CROWD_LEVEL,
    OTHER
}
