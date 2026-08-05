package com.typenull.pingdom.place;

import java.util.List;

public record PlaceLifecycleScenario(String name, String method, String endpoint, int expectedStatus,
                                     String expectedErrorCode, List<String> assertions) {
}
