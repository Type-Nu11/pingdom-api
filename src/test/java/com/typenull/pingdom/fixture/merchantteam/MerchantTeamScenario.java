package com.typenull.pingdom.fixture.merchantteam;

import java.util.List;

public record MerchantTeamScenario(String name, MerchantTeamScenarioType type, String method, String endpoint,
                                   int expectedStatus, String expectedErrorCode, List<String> assertions) {
}
