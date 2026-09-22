package com.typenull.pingdom.fixture.merchantteam;

import java.util.List;

/** 팀 API의 요청 방식·경로와 기대 상태·오류·검증 항목을 보관한다. 실제 요청 실행 결과는 아니다. */
public record MerchantTeamScenario(String name, MerchantTeamScenarioType type, String method, String endpoint,
                                   int expectedStatus, String expectedErrorCode, List<String> assertions) {
}
