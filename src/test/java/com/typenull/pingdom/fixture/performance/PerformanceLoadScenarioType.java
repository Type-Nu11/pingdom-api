package com.typenull.pingdom.fixture.performance;

/** 성능 시나리오의 정상·경계·실패·재시도 범위를 구분하는 fixture 분류다. */
public enum PerformanceLoadScenarioType {
    NORMAL,
    BOUNDARY,
    FAILURE,
    RETRY
}
