package com.typenull.pingdom.integration.swagger.fixture;

/**
 * 문서 호환성 fixture가 정상 응답, 경계, 실패, 재검증 상황을 포함하도록 분류한다.
 */
public enum OpenApiCompatibilityScenarioType {
    NORMAL,
    BOUNDARY,
    FAILURE,
    RETRY
}
