package com.typenull.pingdom.fixture.moderation.place;

import java.util.List;

/** 중복 장소 후보 API의 요청 경로와 기대 HTTP 상태·오류·검증 항목을 묶는 계약 데이터. */
public record PlaceDuplicateScenario(String name, String method, String endpoint, int expectedStatus,
                                     String expectedErrorCode, List<String> assertions) {
}
