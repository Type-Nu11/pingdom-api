package com.typenull.pingdom.fixture.performance;

import java.util.List;

public record PingdomPerformanceLoadFixture(
        List<FixtureUser> users,
        List<FixturePlace> places,
        List<FixtureReport> reports,
        List<FixtureRetryEvent> retryEvents,
        List<PerformanceLoadScenario> scenarios
) {
}
