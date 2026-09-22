package com.typenull.pingdom.fixture.performance;

import java.util.List;

/** 성능 시나리오의 사용자·장소·신고·재시도 이벤트와 요청 계약을 하나로 전달한다. */
public record PingdomPerformanceLoadFixture(
        List<FixtureUser> users,
        List<FixturePlace> places,
        List<FixtureReport> reports,
        List<FixtureRetryEvent> retryEvents,
        List<PerformanceLoadScenario> scenarios
) {
}
