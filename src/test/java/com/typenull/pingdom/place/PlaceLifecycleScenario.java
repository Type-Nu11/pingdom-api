package com.typenull.pingdom.place;

import java.util.List;

/** 장소 수명주기 fixture의 요청·기대 상태·선택적 오류 코드·검증 문구를 담습니다. */
public record PlaceLifecycleScenario(String name, String method, String endpoint, int expectedStatus,
                                     String expectedErrorCode, List<String> assertions) {
}
