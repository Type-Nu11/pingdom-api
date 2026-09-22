package com.typenull.pingdom.verification.domain;

/** Scout 현장 제보의 분류다. 방문자 제보의 구조화 값 규칙과 별도로 본문·증빙으로 내용을 전달한다. */
public enum ScoutFieldReportType {
    PLACE_INFORMATION,
    OPERATING_HOURS,
    LOCATION,
    CLOSED_PLACE,
    WAIT_TIME,
    CROWD_LEVEL,
    SAFETY,
    OTHER
}
